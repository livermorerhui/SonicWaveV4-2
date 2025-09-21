package com.example.sonicwavev4.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.sonicwavev4.databinding.DialogAddCustomerBinding
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.ui.user.UserViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class AddCustomerDialogFragment : DialogFragment() {

    private var _binding: DialogAddCustomerBinding? = null
    private val binding get() = _binding!!

    private val userViewModel: UserViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddCustomerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.closeButton.setOnClickListener {
            dismiss()
        }

        binding.dobEditText.setOnClickListener {
            showDatePickerDialog()
        }

        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString()
            val email = binding.emailEditText.text.toString()

            if (name.isBlank() || email.isBlank()) {
                Toast.makeText(requireContext(), "客户姓名和邮箱不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val customer = Customer(
                name = name,
                email = email,
                dateOfBirth = binding.dobEditText.text.toString(),
                gender = binding.genderEditText.text.toString(),
                phone = binding.phoneEditText.text.toString(),
                height = binding.heightEditText.text.toString().toDoubleOrNull() ?: 0.0,
                weight = binding.weightEditText.text.toString().toDoubleOrNull() ?: 0.0
            )

            userViewModel.addCustomer(customer)
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            val formattedDate = String.format("%d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
            binding.dobEditText.setText(formattedDate)
        }, year, month, day).show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            userViewModel.addCustomerResult.collectLatest { result ->
                result?.let {
                    if (it.isSuccess) {
                        Toast.makeText(requireContext(), "客户添加成功", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), "添加失败: ${it.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                    userViewModel.resetAddCustomerResult() // Reset the state
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
