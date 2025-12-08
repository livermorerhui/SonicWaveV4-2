package com.example.sonicwavev4.ui.humeds

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.databinding.DialogHumedsTestBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HumedsTestDialogFragment : DialogFragment() {

    private var _binding: DialogHumedsTestBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HumedsTestViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setTitle("Humeds 接口测试")
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogHumedsTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editMobile.addTextChangedListener {
            viewModel.updateMobile(it?.toString().orEmpty())
        }
        binding.editPassword.addTextChangedListener {
            viewModel.updatePassword(it?.toString().orEmpty())
        }
        binding.editRegionCode.addTextChangedListener {
            viewModel.updateRegionCode(it?.toString().orEmpty())
        }

        binding.btnSubmit.setOnClickListener {
            viewModel.submit()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnSubmit.isEnabled = !state.isLoading

                    val tokenLabel = state.token?.let { "Token: $it" } ?: "Token: (无)"
                    binding.textToken.text = tokenLabel

                    binding.textResult.text = state.rawText.orEmpty()
                    binding.textError.text = state.errorMessage.orEmpty()

                    if (binding.editMobile.text.toString() != state.mobile) {
                        binding.editMobile.setText(state.mobile)
                        binding.editMobile.setSelection(binding.editMobile.text?.length ?: 0)
                    }
                    if (binding.editPassword.text.toString() != state.password) {
                        binding.editPassword.setText(state.password)
                        binding.editPassword.setSelection(binding.editPassword.text?.length ?: 0)
                    }
                    if (binding.editRegionCode.text.toString() != state.regionCode) {
                        binding.editRegionCode.setText(state.regionCode)
                        binding.editRegionCode.setSelection(binding.editRegionCode.text?.length ?: 0)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
