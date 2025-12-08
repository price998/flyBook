package com.example.myapplication.ui.common.managers

import androidx.drawerlayout.widget.DrawerLayout
import com.example.myapplication.databinding.IncludeSidebarCommonBinding

/**
 * 侧边栏管理器
 * 
 * 负责管理侧边栏的所有交互逻辑，包括：
 * - 按钮点击事件处理
 * - 选中状态管理
 * - 侧边栏打开/关闭控制
 * 
 * 使用示例：
 * ```kotlin
 * val sidebarManager = SidebarManager(
 *     drawerLayout = binding.drawerLayout,
 *     sidebarBinding = binding.includeSidebar,
 *     listener = object : SidebarManager.Listener {
 *         override fun onItemClick(item: SidebarItem) {
 *             when (item) {
 *                 SidebarItem.SEARCH -> startActivity(Intent(this@MyActivity, com.example.myapplication.ui.search.SearchActivity::class.java))
 *                 SidebarItem.NEW_CHAT -> startNewChat()
 *                 SidebarItem.KNOWLEDGE_BASE -> navigateToKnowledgeBase()
 *                 SidebarItem.GENERATE_FAKE_DATA -> generateFakeData()
 *             }
 *         }
 *     }
 * )
 * sidebarManager.setup()
 * ```
 */
class SidebarManager(
    private val drawerLayout: DrawerLayout,
    private val sidebarBinding: IncludeSidebarCommonBinding,
    private val listener: Listener
) {
    
    /**
     * 侧边栏项目枚举
     */
    enum class SidebarItem {
        /** 搜索 */
        SEARCH,
        /** 新建对话 */
        NEW_CHAT,
        /** 知识库 */
        KNOWLEDGE_BASE,
        /** 生成假数据（调试用） */
        GENERATE_FAKE_DATA
    }
    
    /**
     * 侧边栏事件监听器
     */
    interface Listener {
        /**
         * 当侧边栏项目被点击时调用
         * @param item 被点击的项目
         */
        fun onItemClick(item: SidebarItem)
    }
    
    /**
     * 设置侧边栏的所有点击监听器
     * 应在Activity的onCreate()中调用
     */
    fun setup() {
        // 搜索按钮
        sidebarBinding.btnSidebarSearch.setOnClickListener {
            closeDrawer()
            listener.onItemClick(SidebarItem.SEARCH)
        }
        
        // 新建对话按钮
        sidebarBinding.btnNewChat.setOnClickListener {
            closeDrawer()
            listener.onItemClick(SidebarItem.NEW_CHAT)
        }
        
        // 知识库按钮
        sidebarBinding.btnKnowledgeBase.setOnClickListener {
            closeDrawer()
            listener.onItemClick(SidebarItem.KNOWLEDGE_BASE)
        }
        
        // 生成假数据按钮（调试用）
        sidebarBinding.btnGenerateFakeData.setOnClickListener {
            closeDrawer()
            listener.onItemClick(SidebarItem.GENERATE_FAKE_DATA)
        }
    }
    
    /**
     * 更新侧边栏的选中状态
     * @param item 要选中的项目，如果为null则清除所有选中状态
     */
    fun updateSelection(item: SidebarItem?) {
        // 重置所有按钮的选中状态
        sidebarBinding.btnNewChat.isSelected = false
        sidebarBinding.btnKnowledgeBase.isSelected = false
        
        // 设置指定项目的选中状态
        when (item) {
            SidebarItem.NEW_CHAT -> sidebarBinding.btnNewChat.isSelected = true
            SidebarItem.KNOWLEDGE_BASE -> sidebarBinding.btnKnowledgeBase.isSelected = true
            else -> {} // SEARCH 和 GENERATE_FAKE_DATA 不需要选中状态
        }
    }
    
    /**
     * 打开侧边栏
     */
    fun openDrawer() {
        drawerLayout.openDrawer(androidx.core.view.GravityCompat.END)
    }
    
    /**
     * 关闭侧边栏
     */
    fun closeDrawer() {
        drawerLayout.closeDrawers()
    }
}
