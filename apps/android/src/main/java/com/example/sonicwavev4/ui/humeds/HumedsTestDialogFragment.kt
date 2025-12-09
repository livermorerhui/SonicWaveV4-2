package com.example.sonicwavev4.ui.humeds

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.util.Log
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
    private var appAccessToken: String? = null
    private var humedsTokenJwt: String? = null

    companion object {
        private const val HUMEDS_PACKAGE_NAME = "com.humeds.epcg"
        const val HUMEDS_TOKEN_EXTRA = "token_jwt"
    }

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
        binding.btnCopyAll.setOnClickListener {
            val tokenText = binding.textToken.text?.toString().orEmpty()
            val rawText = binding.textResult.text?.toString().orEmpty()
            val errorText = binding.textError.text?.toString().orEmpty()

            val combined = buildString {
                appendLine("Token 部分：")
                appendLine(tokenText.ifBlank { "(无 Token)" })
                appendLine()
                appendLine("原始返回：")
                appendLine(rawText.ifBlank { "(无原始返回)" })
                if (errorText.isNotBlank()) {
                    appendLine()
                    appendLine("错误信息：")
                    appendLine(errorText)
                }
            }

            if (combined.isNotBlank()) {
                val clipboard = requireContext()
                    .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("HumedsTokenDebug", combined)
                clipboard.setPrimaryClip(clip)

                Toast.makeText(requireContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "当前没有可复制的内容", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnLaunchHumeds.setOnClickListener {
            launchHumedsApp()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnSubmit.isEnabled = !state.isLoading

                    humedsTokenJwt = state.humedsTokenJwt
                    val tokenLabel = state.humedsTokenJwt?.let { "Token: $it" } ?: "Token: (无)"
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

    private fun launchHumedsApp() {
        val context = requireContext()
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(HUMEDS_PACKAGE_NAME)

        if (launchIntent == null) {
            Toast.makeText(context, "未安装 Humeds APP", Toast.LENGTH_SHORT).show()
            return
        }

        if (humedsTokenJwt.isNullOrBlank()) {
            Toast.makeText(context, "请先获取 Humeds token_jwt", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(
            "HumedsBridge",
            "launchHumedsApp appAccessToken=${appAccessToken?.take(20)}, humedsTokenJwt=${humedsTokenJwt?.take(200)}"
        )
        launchIntent.putExtra("token_jwt", humedsTokenJwt)

        try {
            startActivity(launchIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "未安装 Humeds APP", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
