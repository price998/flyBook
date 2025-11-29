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
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(topics[position])
                }
            }
        }

        fun bind(topic: RecommendedTopicEntity) {
            binding.topicTitle.text = topic.title
            if (!topic.prompt.isNullOrEmpty()) {
                binding.topicSubtitle.text = topic.prompt
                binding.topicSubtitle.visibility = View.VISIBLE
            } else {
                binding.topicSubtitle.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder {
        val binding = ItemTopicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TopicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        holder.bind(topics[position])
    }

    override fun getItemCount(): Int = topics.size
}
