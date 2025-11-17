package com.example.sonicwavev4.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
