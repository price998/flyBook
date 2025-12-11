package com.example.myapplication.ui.main.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.model.RecommendedTopic
import com.example.myapplication.databinding.ItemTopicBinding

// 为了给RecyclerView提供数据和视图
class TopicAdapter(
        private var topics: List<RecommendedTopic> = emptyList(),
        private val onItemClick: (RecommendedTopic) -> Unit
) : RecyclerView.Adapter<TopicAdapter.TopicViewHolder>() {

    // 当数据列表topics有更新时，用这个方法来刷新RecyclerView
    // 使用DiffUtil计算差异，优化刷新性能
    fun updateData(newTopics: List<RecommendedTopic>) {
        // 创建一个子实例
        val diffCallback = TopicDiffCallback(topics, newTopics)
        // 计算差异结果
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        topics = newTopics
        diffResult.dispatchUpdatesTo(this)
    }

    // 用于比较新旧数据列表
    private class TopicDiffCallback(
        private val oldList: List<RecommendedTopic>,
        private val newList: List<RecommendedTopic>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        // 比较索引
        // 判断是否是同一条item，return一个Boolean
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItemPosition == newItemPosition
        }

        // 比较内容
        // 判断内容是否相同，内容同不刷新，内容不同刷新
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    inner class TopicViewHolder(private val binding: ItemTopicBinding) :
            RecyclerView.ViewHolder(binding.root) {

        // 在viewHolder创建时
        init {
            // 点击事件的逻辑
            binding.root.setOnClickListener {
                val position = adapterPosition
                android.util.Log.d("TopicAdapter", "话题被点击，position: $position")
                if (position != RecyclerView.NO_POSITION && topics.isNotEmpty()) {
                    // 还原出真实的Item索引
                    val actualPosition = getPseudoRandomIndex(position, topics.size)
                    android.util.Log.d("TopicAdapter", "实际位置: $actualPosition, 话题: ${topics[actualPosition].title}")
                    // 把真实的item数据传给外部处理
                    onItemClick(topics[actualPosition])
                } else {
                    android.util.Log.w("TopicAdapter", "无效的点击，position: $position, topics.size: ${topics.size}")
                }
            }
        }

        // 把RecommendedTopic数据对象显示到item的控件上
        fun bind(topic: RecommendedTopic) {
            binding.topicTitle.text = topic.title
        }
    }

    // 创建item布局与ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder {
        val binding = ItemTopicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TopicViewHolder(binding)
    }

    // 重绘
    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        if (topics.isNotEmpty()) {
            // 将无限滚动的position映射到真实数据的索引
            val actualPosition = getPseudoRandomIndex(position, topics.size)
            // 返回给viewHolder展示
            holder.bind(topics[actualPosition])
        }
    }

    /**
     * 伪随机索引生成算法
     * =(当前位置 * 质数 + 偏移量) % 列表长度
     */
    private fun getPseudoRandomIndex(position: Int, size: Int): Int {
        return kotlin.math.abs((position * 17 + 3) % size)
    }

    // 返回整型最大值，实现无限长度
    override fun getItemCount(): Int = if (topics.isEmpty()) 0 else Int.MAX_VALUE
}
