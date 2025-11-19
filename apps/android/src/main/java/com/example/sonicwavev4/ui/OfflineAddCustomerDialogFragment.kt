package com.example.sonicwavev4.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.view.ViewGroup.LayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.sonicwavev4.databinding.DialogAddOfflineCustomerBinding
import com.example.sonicwavev4.ui.user.UserViewModel

class OfflineAddCustomerDialogFragment : DialogFragment() {

    private var _binding: DialogAddOfflineCustomerBinding? = null
    private val binding get() = _binding!!

    private val userViewModel: UserViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddOfflineCustomerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
    }

    // 设置离线模式新增客户对话框的宽高-修改文档dimensions.xml中的值
    override fun onStart() {
        super.onStart()
        val width = resources.getDimensionPixelSize(com.example.sonicwavev4.R.dimen.offline_add_customer_dialog_width)
        val height = resources.getDimensionPixelSize(com.example.sonicwavev4.R.dimen.offline_add_customer_dialog_height)
        dialog?.window?.setLayout(width, height)
    }

    private fun setupClickListeners() {
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                binding.nameInputLayout.error = "客户姓名不能为空"
                return@setOnClickListener
            }
            binding.nameInputLayout.error = null
            userViewModel.addOfflineCustomer(name)
            Toast.makeText(requireContext(), "已添加本地客户", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = OfflineAddCustomerDialogFragment()
    }
}
