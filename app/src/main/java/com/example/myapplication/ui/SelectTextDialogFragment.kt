package com.example.myapplication.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.myapplication.databinding.DialogSelectTextBinding

class SelectTextDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_TEXT = "arg_text"

        fun newInstance(text: String): SelectTextDialogFragment {
            return SelectTextDialogFragment().apply {
                arguments = bundleOf(ARG_TEXT to text)
            }
        }
    }

    private var _binding: DialogSelectTextBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSelectTextBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val params = window.attributes
            params.gravity = Gravity.BOTTOM
            window.attributes = params
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val text = arguments?.getString(ARG_TEXT).orEmpty()
        binding.tvSelectable.text = text
        binding.tvSelectable.setTextIsSelectable(true)

        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
