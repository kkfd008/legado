package io.legado.app.ui.book.manage

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.ActivityArrangeBookBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.book.contains
import io.legado.app.help.book.getFileSize
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.book.tag.TagManageDialog
import io.legado.app.ui.book.tag.TagSelectDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.dpToPx
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 书架管理
 */
class BookshelfManageActivity :
    VMBaseActivity<ActivityArrangeBookBinding, BookshelfManageViewModel>(),
    PopupMenu.OnMenuItemClickListener,
    SelectActionBar.CallBack,
    BookAdapter.CallBack,
    SourcePickerDialog.Callback,
    GroupSelectDialog.CallBack,
    TagSelectDialog.CallBack {

    override val binding by viewBinding(ActivityArrangeBookBinding::inflate)
    override val viewModel by viewModels<BookshelfManageViewModel>()
    override val groupList: ArrayList<BookGroup> = arrayListOf()
    private val tagList: ArrayList<BookTag> = arrayListOf()
    private val groupRequestCode = 22
    private val addToGroupRequestCode = 34
    private val addTagsRequestCode = 44
    private val removeFromGroupRequestCode = 54
    private val removeTagsRequestCode = 64
    private var currentTagId: Long = -1
    private var currentTagName: String = ""
    private val NO_TAG_ID = -2L
    private val adapter by lazy { BookAdapter(this, this) }
    private val itemTouchCallback by lazy { ItemTouchCallback(adapter) }
    private var booksFlowJob: Job? = null
    private var menu: Menu? = null
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var books: List<Book>? = null
    private val waitDialog by lazy { WaitDialog(this) }
    private val exportDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                if (uri.toString().isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(uri.toString())
                }
                customView { alertBinding.root }
                okButton {
                    sendToClip(uri.toString())
                }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel.groupId = intent.getLongExtra("groupId", -1)
        lifecycleScope.launch {
            viewModel.groupName = withContext(IO) {
                appDb.bookGroupDao.getByID(viewModel.groupId)?.groupName
                    ?: getString(R.string.no_group)
            }
            upTitle()
        }
        initSearchView()
        initRecyclerView()
        initOtherView()
        initGroupData()
        initTagData()
        upBookDataByGroupId()
    }

    override fun observeLiveBus() {
        viewModel.batchChangeSourceState.observe(this) {
            if (it) {
                waitDialog.setText(R.string.change_source_batch)
                waitDialog.show()
            } else {
                waitDialog.dismiss()
            }
        }
        viewModel.batchChangeSourceProcessLiveData.observe(this) {
            waitDialog.setText(it)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.bookshelf_manage, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        menu.findItem(R.id.menu_open_book_info_by_click_title)?.isChecked =
            AppConfig.openBookInfoByClickTitle
        upMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun selectAll(selectAll: Boolean) {
        adapter.selectAll(selectAll)
    }

    override fun revertSelection() {
        adapter.revertSelection()
    }

    override fun onClickSelectBarMainAction() {
        selectTag(addTagsRequestCode, 0L)
    }

    private fun upTitle() {
        if (currentTagId > 0) {
            searchView.queryHint = getString(R.string.screen) + " • " + currentTagName
        } else {
            searchView.queryHint = getString(R.string.screen) + " • " + viewModel.groupName
        }
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                upBookData()
                return false
            }

        })
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        itemTouchCallback.isCanDrag = AppConfig.bookshelfSort == 3
        val dragSelectTouchHelper: DragSelectTouchHelper =
            DragSelectTouchHelper(adapter.dragSelectCallback).setSlideArea(16, 50)
        dragSelectTouchHelper.attachToRecyclerView(binding.recyclerView)
        // When this page is opened, it is in selection mode
        dragSelectTouchHelper.activeSlideSelect()
        // Note: need judge selection first, so add ItemTouchHelper after it.
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun initOtherView() {
        binding.selectActionBar.inflateMenu(R.menu.bookshelf_menage_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
        waitDialog.setOnCancelListener {
            viewModel.batchChangeSourceCoroutine?.cancel()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initGroupData() {
        lifecycleScope.launch {
            appDb.bookGroupDao.flowAll().catch {
                AppLog.put("书架管理界面获取分组数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                groupList.clear()
                groupList.addAll(it)
                adapter.notifyDataSetChanged()
                upMenu()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initTagData() {
        lifecycleScope.launch {
            appDb.bookTagDao.flowSelect().catch {
                AppLog.put("书架管理界面获取标签数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                tagList.clear()
                tagList.addAll(it)
                adapter.notifyDataSetChanged()
                upMenu()
            }
        }
    }

    private fun upBookDataByGroupId() {
        booksFlowJob?.cancel()
        booksFlowJob = lifecycleScope.launch {
            val bookSort = AppConfig.getBookSortByGroupId(viewModel.groupId)
            val flow = when {
                currentTagId == NO_TAG_ID -> {
                    appDb.bookDao.flowSearchByNoTag()
                }
                currentTagId > 0 -> {
                    appDb.bookDao.flowSearchByTag(currentTagId)
                }
                else -> {
                    appDb.bookDao.flowByGroup(viewModel.groupId, appDb.bookGroupDao.idsSum, appDb.bookGroupDao.getByID(BookGroup.IdNetNone)?.show != true)
                }
            }
            flow.map { list ->
                when (bookSort) {
                    1 -> if (AppConfig.bookshelfSortAscending)
                        list.sortedBy { it.latestChapterTime }
                    else
                        list.sortedByDescending { it.latestChapterTime }
                    2 -> if (AppConfig.bookshelfSortAscending)
                        list.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }
                    else
                        list.sortedWith { o1, o2 -> o2.name.cnCompare(o1.name) }
                    3 -> if (AppConfig.bookshelfSortAscending)
                        list.sortedBy { it.order }
                    else
                        list.sortedByDescending { it.order }
                    4 -> if (AppConfig.bookshelfSortAscending)
                        list.sortedBy { max(it.latestChapterTime, it.durChapterTime) }
                    else
                        list.sortedByDescending { max(it.latestChapterTime, it.durChapterTime) }
                    5 -> if (AppConfig.bookshelfSortAscending)
                        list.sortedWith { o1, o2 -> o1.author.cnCompare(o2.author) }
                    else
                        list.sortedWith { o1, o2 -> o2.author.cnCompare(o1.author) }
                    6 -> if (AppConfig.bookshelfSortAscending)
                        list.sortedBy { it.getFileSize() }
                    else
                        list.sortedByDescending { it.getFileSize() }
                    7 -> if (AppConfig.bookshelfSortAscending)
                        list.sortedBy { it.rating }
                    else
                        list.sortedByDescending { it.rating }
                    else -> if (AppConfig.bookshelfSortAscending)
                        list.sortedBy { it.durChapterTime }
                    else
                        list.sortedByDescending { it.durChapterTime }
                }
            }.catch {
                AppLog.put("书架管理界面获取书籍列表失败\n${it.localizedMessage}", it)
            }.flowOn(IO)
                .conflate().collect {
                    books = it
                    upBookData()
                    itemTouchCallback.isCanDrag = bookSort == 3
                }
        }
    }

    private fun upBookData() {
        books?.let { books ->
            val searchKey = searchView.query
            if (searchKey.isNullOrEmpty()) {
                adapter.setItems(books)
            } else {
                books.filter {
                    it.contains(searchKey.toString())
                }.let {
                    adapter.setItems(it)
                }
            }
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_tag_manage -> showDialogFragment<TagManageDialog>()
            R.id.menu_open_book_info_by_click_title -> {
                AppConfig.openBookInfoByClickTitle = !item.isChecked
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            }

            R.id.menu_export_all_use_book_source -> viewModel.saveAllUseBookSourceToFile { file ->
                exportDir.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = HandleFileContract.FileData(
                        "bookSource.json",
                        file,
                        "application/json"
                    )
                }
            }

            else -> if (item.groupId == R.id.menu_group) {
                viewModel.groupName = item.title.toString()
                currentTagId = -1
                currentTagName = ""
                upTitle()
                viewModel.groupId =
                    appDb.bookGroupDao.getByName(item.title.toString())?.groupId ?: 0
                upBookDataByGroupId()
            } else if (item.groupId == R.id.menu_tag) {
                currentTagName = item.title.toString()
                currentTagId = item.itemId.toLong()
                if (currentTagId == 0L) {
                    currentTagName = ""
                }
                viewModel.groupName = ""
                upTitle()
                upBookDataByGroupId()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_del_selection -> alertDelSelection()
            R.id.menu_update_enable ->
                viewModel.upCanUpdate(adapter.selection, true)

            R.id.menu_update_disable ->
                viewModel.upCanUpdate(adapter.selection, false)

            R.id.menu_add_to_group -> selectGroup(addToGroupRequestCode, 0L)
            R.id.menu_remove_from_group -> selectGroup(removeFromGroupRequestCode, 0L)
            R.id.menu_add_tags -> selectTag(addTagsRequestCode, 0L)
            R.id.menu_remove_tags -> selectTag(removeTagsRequestCode, 0L)
            R.id.menu_change_source -> showDialogFragment<SourcePickerDialog>()
            R.id.menu_clear_cache -> viewModel.clearCache(adapter.selection)
            R.id.menu_check_selected_interval -> adapter.checkSelectedInterval()
        }
        return false
    }

    private fun upMenu() {
        menu?.findItem(R.id.menu_book_group)?.subMenu?.let { subMenu ->
            subMenu.removeGroup(R.id.menu_group)
            groupList.forEach { bookGroup ->
                subMenu.add(R.id.menu_group, bookGroup.order, Menu.NONE, bookGroup.groupName)
            }
        }
        menu?.findItem(R.id.menu_book_tag)?.subMenu?.let { subMenu ->
            subMenu.removeGroup(R.id.menu_tag)
            subMenu.add(R.id.menu_tag, 0, Menu.NONE, getString(R.string.all))
            subMenu.add(R.id.menu_tag, NO_TAG_ID.toInt(), Menu.NONE, getString(R.string.no_tag))
            tagList.forEach { bookTag ->
                subMenu.add(R.id.menu_tag, bookTag.tagId.toInt(), Menu.NONE, bookTag.name)
            }
        }
    }

    private fun alertDelSelection() {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            val checkBox = CheckBox(this@BookshelfManageActivity).apply {
                setText(R.string.delete_book_file)
                isChecked = LocalConfig.deleteBookOriginal
            }
            val view = LinearLayout(this@BookshelfManageActivity).apply {
                setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                addView(checkBox)
            }
            customView { view }
            okButton {
                LocalConfig.deleteBookOriginal = checkBox.isChecked
                viewModel.deleteBook(adapter.selection, checkBox.isChecked)
            }
            noButton()
        }
    }

    override fun selectGroup(requestCode: Int, groupId: Long) {
        showDialogFragment(
            GroupSelectDialog(groupId, requestCode)
        )
    }

    override fun selectTag(requestCode: Int, tags: Long) {
        showDialogFragment(
            TagSelectDialog(tags, requestCode)
        )
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        when (requestCode) {
            groupRequestCode -> adapter.selection.let { books ->
                val array = Array(books.size) {
                    books[it].copy(group = groupId)
                }
                viewModel.updateBook(*array)
            }

            adapter.groupRequestCode -> {
                adapter.actionItem?.let {
                    viewModel.updateBook(it.copy(group = groupId))
                }
            }

            addToGroupRequestCode -> adapter.selection.let { books ->
                val array = Array(books.size) { index ->
                    val book = books[index]
                    book.copy(group = book.group or groupId)
                }
                viewModel.updateBook(*array)
            }

            removeFromGroupRequestCode -> adapter.selection.let { books ->
                val array = Array(books.size) { index ->
                    val book = books[index]
                    book.copy(group = book.group and groupId.inv())
                }
                viewModel.updateBook(*array)
            }
        }
    }

    override fun upTags(requestCode: Int, tags: Long) {
        when (requestCode) {
            addTagsRequestCode -> adapter.selection.let { books ->
                val array = Array(books.size) { index ->
                    val book = books[index]
                    book.copy(tags = book.tags or tags)
                }
                viewModel.updateBook(*array)
            }

            removeTagsRequestCode -> adapter.selection.let { books ->
                val array = Array(books.size) { index ->
                    val book = books[index]
                    book.copy(tags = book.tags and tags.inv())
                }
                viewModel.updateBook(*array)
            }

            adapter.tagRequestCode -> {
                adapter.actionItem?.let {
                    viewModel.updateBook(it.copy(tags = tags))
                }
            }
        }
    }

    override fun upSelectCount() {
        binding.selectActionBar.upCountView(adapter.selection.size, adapter.getItems().size)
    }

    override fun updateBook(vararg book: Book) {
        viewModel.updateBook(*book)
    }

    override fun deleteBook(book: Book) {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            var checkBox: CheckBox? = null
            if (book.isLocal) {
                checkBox = CheckBox(this@BookshelfManageActivity).apply {
                    setText(R.string.delete_book_file)
                    isChecked = LocalConfig.deleteBookOriginal
                }
                val view = LinearLayout(this@BookshelfManageActivity).apply {
                    setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                    addView(checkBox)
                }
                customView { view }
            }
            okButton {
                if (checkBox != null) {
                    LocalConfig.deleteBookOriginal = checkBox.isChecked
                }
                viewModel.deleteBook(listOf(book), LocalConfig.deleteBookOriginal)
            }
        }
    }

    override fun openBook(book: Book) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
        }
    }

    override fun sourceOnClick(source: BookSource) {
        viewModel.changeSource(adapter.selection, source)
        viewModel.batchChangeSourceState.value = true
    }

}