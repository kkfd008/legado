package io.legado.app.ui.book.searchContent

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.allViews
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSearchKeyword
import io.legado.app.databinding.ActivitySearchContentBinding
import io.legado.app.help.IntentData
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.widget.recycler.UpLinearLayoutManager
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyNavigationBarMargin
import io.legado.app.utils.applyTint
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.showSoftInput
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SearchContentActivity :
    VMBaseActivity<ActivitySearchContentBinding, SearchContentViewModel>(),
    SearchContentAdapter.Callback,
    BookSearchHistoryAdapter.CallBack {

    override val binding by viewBinding(ActivitySearchContentBinding::inflate)
    override val viewModel by viewModels<SearchContentViewModel>()
    private val adapter by lazy { SearchContentAdapter(this, this) }
    private val historyAdapter by lazy {
        BookSearchHistoryAdapter(this, this).apply {
            setHasStableIds(true)
        }
    }
    private val mLayoutManager by lazy { UpLinearLayoutManager(this) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var durChapterIndex = 0
    private var searchJob: Job? = null
    private var initJob: Job? = null
    private var historyFlowJob: Job? = null
    private var isLoadingPage = false
    // 搜索会话序号：每次发起新搜索自增，用于丢弃被取消的旧搜索的异步结果，防止串台
    private var searchSession = 0

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val bbg = bottomBackground
        val btc = getPrimaryTextColor(ColorUtils.isColorLight(bbg))
        binding.llSearchBaseInfo.setBackgroundColor(bbg)
        binding.llSearchBaseInfo.applyNavigationBarMargin()
        binding.tvCurrentSearchInfo.setTextColor(btc)
        binding.ivSearchContentTop.setColorFilter(btc)
        binding.ivSearchContentBottom.setColorFilter(btc)
        val searchResultList = IntentData.get<List<SearchResult>>("searchResultList")
        val position = intent.getIntExtra("searchResultIndex", 0)
        val noSearchResult = searchResultList == null
        initSearchView(noSearchResult)
        initRecyclerView()
        initView()
        val bookUrl = intent.getStringExtra("bookUrl") ?: return
        viewModel.initBook(bookUrl) {
            initSearchResultList(searchResultList, position)
            initBook(noSearchResult)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.content_search, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_enable_replace)?.isChecked = viewModel.replaceEnabled
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_enable_replace -> {
                viewModel.replaceEnabled = !viewModel.replaceEnabled
                item.isChecked = viewModel.replaceEnabled
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initSearchResultList(list: List<SearchResult>?, position: Int) {
        list ?: return
        viewModel.searchResultList.addAll(list)
        viewModel.searchResultCounts = list.size
        adapter.setItems(list)
        binding.recyclerView.scrollToPosition(position)
    }

    private fun initSearchView(requestFocus: Boolean) {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.search)
        if (requestFocus) searchView.isIconified = false
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                val trimmedQuery = query.trim()
                startContentSearch(trimmedQuery)
                searchView.clearFocus()
                // 保存搜索关键字
                if (trimmedQuery.isNotBlank()) {
                    viewModel.saveSearchKey(trimmedQuery)
                }
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                upHistory(newText.trim())
                return false
            }
        })
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                visibleInputHelp(true)
            } else {
                visibleInputHelp(false)
            }
        }
        // 有缓存结果时直接展示结果列表，不展示搜索历史输入帮助
        visibleInputHelp(requestFocus)
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = mLayoutManager
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE
                    && !rv.canScrollVertically(1)
                    && viewModel.searchResultList.isNotEmpty()
                ) {
                    // 滚动到底部，加载下一页结果
                    loadNextPage()
                }
            }
        })
        binding.rvHistoryKey.layoutManager = com.google.android.flexbox.FlexboxLayoutManager(this)
        binding.rvHistoryKey.adapter = historyAdapter
        binding.rvHistoryKey.applyNavigationBarMargin()
    }

    private fun initView() {
        binding.ivSearchContentTop.setOnClickListener {
            mLayoutManager.scrollToPositionWithOffset(0, 0)
        }
        binding.ivSearchContentBottom.setOnClickListener {
            if (adapter.itemCount > 0) {
                mLayoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
            }
        }
        binding.tvCurrentSearchInfo.setOnClickListener {
            searchView.allViews.forEach { view ->
                if (view is EditText) {
                    view.showSoftInput()
                    return@setOnClickListener
                }
            }
        }
        binding.fbStop.setOnClickListener {
            searchJob?.cancel()
        }
        binding.tvClearHistory.setOnClickListener { alertClearHistory() }
    }

    @SuppressLint("SetTextI18n")
    private fun initBook(submit: Boolean = true) {
        binding.tvCurrentSearchInfo.text =
            this.getString(R.string.search_content_size) + ": ${viewModel.searchResultCounts}"
        viewModel.book?.let {
            initCacheFileNames(it)
            durChapterIndex = it.durChapterIndex
            intent.getStringExtra("searchWord")?.let { searchWord ->
                searchView.setQuery(searchWord, submit)
            }
        }
    }

    private fun initCacheFileNames(book: Book) {
        initJob = lifecycleScope.launch {
            withContext(IO) {
                viewModel.cacheChapterNames.addAll(BookHelp.getChapterFiles(book))
            }
            adapter.notifyItemRangeChanged(0, adapter.itemCount, true)
        }
    }

    override fun observeLiveBus() {
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (book, chapter) ->
            viewModel.book?.bookUrl?.let { bookUrl ->
                if (book.bookUrl == bookUrl) {
                    viewModel.cacheChapterNames.add(chapter.getFileName())
                    adapter.notifyItemChanged(chapter.index, true)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun startContentSearch(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        // 新搜索开始：重置分页标志，避免上次分页任务被取消后标志残留导致永久卡死
        isLoadingPage = false
        val session = ++searchSession
        adapter.clearItems()
        viewModel.searchResultList.clear()
        viewModel.pages.clear()
        viewModel.currentPage = 0
        viewModel.lastQuery = query
        binding.refreshProgressBar.isAutoLoading = true
        binding.fbStop.visible()
        searchJob = lifecycleScope.launch(IO) {
            initJob?.join()
            kotlin.runCatching {
                viewModel.initPageSearch()
                val results = viewModel.searchPage(query)
                if (results.isEmpty()) {
                    viewModel.hasMore = false
                }
                binding.tvCurrentSearchInfo.post {
                    // 只有当前会话才允许写回结果，丢弃被取消的旧搜索结果，防止串台
                    if (session != searchSession) return@post
                    if (results.isNotEmpty()) {
                        viewModel.pages.add(results)
                        viewModel.searchResultList.addAll(results)
                        adapter.setItems(results)
                    } else {
                        val noSearchResult =
                            SearchResult(resultText = getString(R.string.search_content_empty))
                        adapter.setItems(listOf(noSearchResult))
                    }
                    updateSearchInfo()
                }
            }.onFailure {
                AppLog.put("全文搜索出错\n${it.localizedMessage}", it)
            }
            binding.tvCurrentSearchInfo.post {
                if (session != searchSession) return@post
                binding.fbStop.invisible()
                binding.refreshProgressBar.isAutoLoading = false
            }
        }
    }

    /**
     * 滚动到底部时加载下一页搜索结果
     */
    private fun loadNextPage() {
        if (!viewModel.hasMore || isLoadingPage) return
        isLoadingPage = true
        binding.refreshProgressBar.isAutoLoading = true
        val session = searchSession
        searchJob = lifecycleScope.launch(IO) {
            kotlin.runCatching {
                viewModel.searchPage(viewModel.lastQuery)
            }.onSuccess { results ->
                if (session != searchSession) return@onSuccess
                if (results.isEmpty()) {
                    viewModel.hasMore = false
                }
                binding.tvCurrentSearchInfo.post {
                    if (session != searchSession) return@post
                    if (results.isNotEmpty()) {
                        viewModel.pages.add(results)
                        viewModel.searchResultList.addAll(results)
                        adapter.addItems(results)
                        updateSearchInfo()
                    }
                    binding.refreshProgressBar.isAutoLoading = false
                    isLoadingPage = false
                }
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) return@onFailure
                AppLog.put("全文搜索出错\n${it.localizedMessage}", it)
                binding.tvCurrentSearchInfo.post {
                    binding.refreshProgressBar.isAutoLoading = false
                    isLoadingPage = false
                }
            }
            // Job 被取消（新搜索 / 停止按钮）时 ensureActive/协程取消会走到这里，
            // 若上面 onFailure 因取消被跳过，在此兜底重置分页标志，防止永久卡死
            if (!isActive && session == searchSession) {
                binding.tvCurrentSearchInfo.post {
                    binding.refreshProgressBar.isAutoLoading = false
                    isLoadingPage = false
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateSearchInfo() {
        binding.tvCurrentSearchInfo.text =
            getString(R.string.search_content_size) + ": ${viewModel.searchResultCounts}"
    }

    private val isLocalBook: Boolean
        get() = viewModel.book?.isLocal == true

    override fun openSearchResult(searchResult: SearchResult, index: Int) {
        searchJob?.cancel()
        postEvent(EventBus.SEARCH_RESULT, viewModel.searchResultList as List<SearchResult>)
        val searchData = Intent()
        val key = System.currentTimeMillis()
        IntentData.put("searchResult$key", searchResult)
        IntentData.put("searchResultList$key", viewModel.searchResultList)
        searchData.putExtra("key", key)
        searchData.putExtra("index", index)
        setResult(RESULT_OK, searchData)
        finish()
    }

    override fun durChapterIndex(): Int {
        return durChapterIndex
    }

    /**
     * 打开关闭输入帮助
     */
    private fun visibleInputHelp(visible: Boolean) {
        if (visible) {
            upHistory(searchView.query.toString())
            binding.llInputHelp.visibility = VISIBLE
            binding.recyclerView.visibility = GONE
        } else {
            binding.llInputHelp.visibility = GONE
            binding.recyclerView.visibility = VISIBLE
        }
    }

    /**
     * 更新搜索历史
     */
    private fun upHistory(key: String? = null) {
        historyFlowJob?.cancel()
        historyFlowJob = lifecycleScope.launch {
            when {
                key.isNullOrBlank() -> appDb.bookSearchKeywordDao.flowByTime()
                else -> appDb.bookSearchKeywordDao.flowSearch(key)
            }.catch {
                AppLog.put("内容搜索界面获取搜索历史数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                historyAdapter.setItems(it)
                if (it.isEmpty()) {
                    binding.tvClearHistory.invisible()
                } else {
                    binding.tvClearHistory.visible()
                }
            }
        }
    }

    override fun searchHistory(key: String) {
        searchView.setQuery(key, true)
    }

    override fun deleteHistory(searchKeyword: BookSearchKeyword) {
        viewModel.deleteHistory(searchKeyword)
    }

    private fun alertClearHistory() {
        alert(R.string.draw) {
            setMessage(R.string.sure_clear_search_history)
            yesButton {
                viewModel.clearHistory()
            }
            noButton()
        }
    }

}