package io.legado.app.ui.book.tag

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookTag
import io.legado.app.databinding.ActivityTagConfigBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 标签配置：展示标签、书籍数量、热门开关，支持编辑/排序/删除与批量操作。
 * 界面与操作参考 TXT 目录规则管理页。
 */
class TagConfigActivity : VMBaseActivity<ActivityTagConfigBinding, TagConfigViewModel>(),
    TagConfigAdapter.CallBack,
    SelectActionBar.CallBack,
    PopupMenu.OnMenuItemClickListener {

    override val viewModel by viewModels<TagConfigViewModel>()
    override val binding by viewBinding(ActivityTagConfigBinding::inflate)
    private val adapter: TagConfigAdapter by lazy {
        TagConfigAdapter(this, this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        initBottomActionBar()
        initData()
    }

    private fun initView() = binding.run {
        recyclerView.setEdgeEffectColor(primaryColor)
        recyclerView.addItemDecoration(VerticalDivider(this@TagConfigActivity))
        recyclerView.adapter = adapter
        // 与 TXT 目录规则列表页一致：长按条目即可上下拖动排序
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(recyclerView)
    }

    private fun initBottomActionBar() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.tag_config_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    private fun initData() {
        lifecycleScope.launch {
            combine(
                appDb.bookTagDao.flowSelect(),
                appDb.bookDao.flowAll()
            ) { tags, books ->
                tags.sortedByDescending { it.order }.map { tag ->
                    TagConfigItem(tag, books.count { book -> (book.tags and tag.tagId) > 0L })
                }
            }.catch {
                AppLog.put("标签配置界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect { items ->
                // 拖拽过程中忽略数据库新数据，避免列表按旧 order 重排、打断正在进行的拖动
                if (adapter.isDragging) return@collect
                adapter.setItems(items, adapter.diffItemCallBack)
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.tag_config, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> {
                if (viewModel.getNextTagId() > 0L) {
                    showDialogFragment(TagEditDialog())
                } else {
                    toastOnUi("标签已达上限(64个)")
                }
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun del(tag: BookTag) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + tag.name)
            noButton()
            yesButton {
                viewModel.del(tag)
            }
        }
    }

    override fun edit(tag: BookTag) {
        // 传副本，避免编辑对话框内的任何就地修改污染 adapter 持有的实体
        showDialogFragment(TagEditDialog(tag.copy()))
    }

    override fun search(tag: BookTag) {
        // 与在搜索框输入 #标签 一致：不自动提交书源搜索，保留本地标签书籍结果
        SearchActivity.start(this, "#${tag.name}", submit = false)
    }

    override fun update(tag: BookTag) {
        viewModel.update(tag)
    }

    override fun toTop(tag: BookTag) {
        viewModel.toTop(tag)
    }

    override fun toBottom(tag: BookTag) {
        viewModel.toBottom(tag)
    }

    override fun upOrder(tags: List<BookTag>) {
        viewModel.upOrder(tags)
    }

    override fun upCountView() {
        binding.selectActionBar.upCountView(adapter.selection.size, adapter.itemCount)
    }

    override fun onClickSelectBarMainAction() {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton { viewModel.del(*adapter.selection.toTypedArray()) }
            noButton()
        }
    }

    override fun revertSelection() {
        adapter.revertSelection()
    }

    override fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            adapter.selectAll()
        } else {
            adapter.revertSelection()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val selection = adapter.selection
        when (item.itemId) {
            R.id.menu_hot_selection -> {
                if (selection.isNotEmpty()) {
                    viewModel.setHot(selection, true)
                    toastOnUi(R.string.tag_hot_enable)
                }
            }
            R.id.menu_unhot_selection -> {
                if (selection.isNotEmpty()) {
                    viewModel.setHot(selection, false)
                    toastOnUi(R.string.tag_hot_disable)
                }
            }
        }
        return true
    }

}
