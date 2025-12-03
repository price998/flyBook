package com.example.myapplication.data

import com.example.myapplication.data.db.RecommendedTopicEntity

/**
 * 默认推荐话题数据
 * 统一管理所有推荐话题，便于维护和扩展
 */
object DefaultTopics {

    /**
     * 获取默认话题列表
     */
    fun getDefaultTopics(): List<RecommendedTopicEntity> {
        return listOf(
            RecommendedTopicEntity(
                title = "🤝 怎样提升团队协作效率？",
                prompt = "怎样提升团队协作效率？"
            ),
            RecommendedTopicEntity(
                title = "📚 飞书知识问答是什么？",
                prompt = "飞书知识问答是什么？"
            ),
            RecommendedTopicEntity(
                title = "⚛️ 解释量子力学",
                prompt = "请用通俗的语言给我解释量子力学的核心概念。"
            ),
            RecommendedTopicEntity(
                title = "📊 OKR和KPI有什么区别？",
                prompt = "请用通俗的语言给我解释OKR和KPI有什么区别。"
            )
        )
    }
}
