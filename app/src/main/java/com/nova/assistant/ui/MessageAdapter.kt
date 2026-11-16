package com.nova.assistant.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.nova.assistant.data.ChatMessage
import com.nova.assistant.data.Role
import com.nova.assistant.databinding.ItemMessageAssistantBinding
import com.nova.assistant.databinding.ItemMessageUserBinding

class MessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<ChatMessage> = emptyList()

    fun submit(list: List<ChatMessage>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(old: Int, new: Int) = items[old].id == list[new].id
            override fun areContentsTheSame(old: Int, new: Int) = items[old] == list[new]
        })
        items = list
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) =
        if (items[position].role == Role.USER) VIEW_USER else VIEW_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_USER) {
            UserHolder(ItemMessageUserBinding.inflate(inflater, parent, false))
        } else {
            AssistantHolder(ItemMessageAssistantBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = items[position]
        when (holder) {
            is UserHolder -> holder.binding.bubble.text = message.content
            is AssistantHolder -> holder.binding.bubble.text = message.content
        }
    }

    private class UserHolder(val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root)
    private class AssistantHolder(val binding: ItemMessageAssistantBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val VIEW_USER = 1
        private const val VIEW_ASSISTANT = 2
    }
}
