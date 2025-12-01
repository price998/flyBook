package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.db.RecommendedTopicEntity
import com.example.myapplication.databinding.ItemTopicBinding

class TopicAdapter(
    private var topics: List<RecommendedTopicEntity> = emptyList(),
    private val onItemClick: (RecommendedTopicEntity) -> Unit
) : RecyclerView.Adapter<TopicAdapter.TopicViewHolder>() {

    fun updateData(newTopics: List<RecommendedTopicEntity>) {
        topics = newTopics
        notifyDataSetChanged()
    }

    inner class TopicViewHolder(private val binding: ItemTopicBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && topics.isNotEmpty()) {
                    val actualPosition = getPseudoRandomIndex(position, topics.size)
                    onItemClick(topics[actualPosition])
                }
            }
        }

        fun bind(topic: RecommendedTopicEntity) {
            binding.topicTitle.text = topic.title
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder {
        val binding = ItemTopicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TopicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        if (topics.isNotEmpty()) {
            val actualPosition = getPseudoRandomIndex(position, topics.size)
            holder.bind(topics[actualPosition])
        }
    }

    // 话题随机展示
    private fun getPseudoRandomIndex(position: Int, size: Int): Int {
        return kotlin.math.abs((position * 17 + 3) % size)
    }

    override fun getItemCount(): Int = if (topics.isEmpty()) 0 else Int.MAX_VALUE
}
