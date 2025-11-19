// 字段定义（class MainActivity {...} 内）
private ActivityMainBinding binding;
private UsbDevice usbDevice;
private boolean isDeviceOpen = false;

private final ExecutorService ioExec = Executors.newSingleThreadExecutor();
private Ad9833Controller ad9833Controller;
private Mcp41010Controller mcpController;
private int mcpCurrentValue = 255;


// initVariable() 内添加
CH341Manager.getInstance().setUsbStateListener(iUsbStateChange);
setBtnEnable(false);
setAd9833ControlsEnabled(false);
setMcpControlsEnabled(false);

ad9833Controller = new Ad9833Controller();
mcpController = new Mcp41010Controller();

// MCP 初始 UI 状态
binding.mcp41010Item.mcp41010ValueSeek.setMax(255);
binding.mcp41010Item.mcp41010ValueSeek.setProgress(mcpCurrentValue);
updateMcpValueLabel(mcpCurrentValue);


// setViewClickListener() 内新增监听
binding.ad9833Item.ad9833SetFreqBtn.setOnClickListener(v -> handleAd9833SetFrequency());
binding.ad9833Item.ad9833ModeSineBtn.setOnClickListener(v -> handleAd9833SetMode(Ad9833Controller.MODE_BITS_SINE, "正弦波"));
binding.ad9833Item.ad9833ModeTriangleBtn.setOnClickListener(v -> handleAd9833SetMode(Ad9833Controller.MODE_BITS_TRIANGLE, "三角波"));
binding.ad9833Item.ad9833ModeSquare1Btn.setOnClickListener(v -> handleAd9833SetMode(Ad9833Controller.MODE_BITS_SQUARE1, "方波/2"));
binding.ad9833Item.ad9833ModeSquare2Btn.setOnClickListener(v -> handleAd9833SetMode(Ad9833Controller.MODE_BITS_SQUARE2, "方波"));
binding.ad9833Item.ad9833ModeOffBtn.setOnClickListener(v -> handleAd9833SetMode(Ad9833Controller.MODE_BITS_OFF, "关闭输出"));

binding.mcp41010Item.mcp41010ValueSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
    @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mcpCurrentValue = progress;
        updateMcpValueLabel(progress);
    }
    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
});
binding.mcp41010Item.mcp41010SetBtn.setOnClickListener(v -> handleMcpSetValue());


// UI enable/disable 辅助方法
private void setAd9833ControlsEnabled(boolean enable) {
    binding.ad9833Item.ad9833SetFreqBtn.setEnabled(enable);
    binding.ad9833Item.ad9833ModeSineBtn.setEnabled(enable);
    binding.ad9833Item.ad9833ModeTriangleBtn.setEnabled(enable);
    binding.ad9833Item.ad9833ModeSquare1Btn.setEnabled(enable);
    binding.ad9833Item.ad9833ModeSquare2Btn.setEnabled(enable);
    binding.ad9833Item.ad9833ModeOffBtn.setEnabled(enable);
}

private void setMcpControlsEnabled(boolean enable) {
    binding.mcp41010Item.mcp41010ValueSeek.setEnabled(enable);
    binding.mcp41010Item.mcp41010SetBtn.setEnabled(enable);
}


// AD9833 频率与波形处理
private void handleAd9833SetFrequency() {
    if (!isDeviceOpen || usbDevice == null) {
        showToast("请先打开设备");
        return;
    }
    String freqStr = binding.ad9833Item.ad9833FreqEdit.getText().toString().trim();
    if (freqStr.isEmpty()) {
        showToast("频率不能为空");
        return;
    }
    double freq;
    try {
        freq = Double.parseDouble(freqStr);
    } catch (NumberFormatException e) {
        showToast("频率格式不正确");
        return;
    }
    ioExec.execute(() -> {
        try {
            ad9833Controller.setFrequency(Ad9833Controller.CHANNEL_0, freq);
            ad9833Controller.setActiveFrequency(Ad9833Controller.CHANNEL_0);
            runOnUiThread(() -> showToast("频率已设置为 " + freq + " Hz"));
        } catch (IllegalArgumentException | CH341LibException e) {
            runOnUiThread(() -> showToast("设置频率失败: " + e.getMessage()));
        }
    });
}

private void handleAd9833SetMode(int modeBits, String label) {
    if (!isDeviceOpen || usbDevice == null) {
        showToast("请先打开设备");
        return;
    }
    ioExec.execute(() -> {
        try {
            ad9833Controller.setMode(modeBits);
            runOnUiThread(() -> showToast("波形已切换为 " + label));
        } catch (CH341LibException e) {
            runOnUiThread(() -> showToast("设置波形失败: " + e.getMessage()));
        }
    });
}


// MCP41010 辅助
private void updateMcpValueLabel(int value) {
    binding.mcp41010Item.mcp41010ValueText.setText(String.valueOf(value));
}

private void handleMcpSetValue() {
    if (!isDeviceOpen || usbDevice == null) {
        showToast("请先打开设备");
        return;
    }
    final int value = binding.mcp41010Item.mcp41010ValueSeek.getProgress();
    ioExec.execute(() -> {
        try {
            mcpController.writeValue(value);
            runOnUiThread(() -> showToast("电位器值已设置为 " + value));
        } catch (CH341LibException e) {
            runOnUiThread(() -> showToast("设置电位器失败: " + e.getMessage()));
        }
    });
}


// 设备初始化
private void initializeAd9833() {
    ioExec.execute(() -> {
        try {
            ad9833Controller.attachDevice(usbDevice);
            ad9833Controller.setCsChannel(0);
            ad9833Controller.begin();
            ad9833Controller.setFrequency(Ad9833Controller.CHANNEL_0, 1000.0);
            ad9833Controller.setActiveFrequency(Ad9833Controller.CHANNEL_0);
            ad9833Controller.setMode(Ad9833Controller.MODE_BITS_SINE);
            runOnUiThread(() -> {
                binding.ad9833Item.ad9833FreqEdit.setText("1000");
                showToast("AD9833 初始化完成");
            });
        } catch (CH341LibException e) {
            runOnUiThread(() -> showToast("AD9833 初始化失败: " + e.getMessage()));
        }
    });
}

private void initializeMcp41010() {
    ioExec.execute(() -> {
        try {
            mcpController.attachDevice(usbDevice);
            mcpController.setCsChannel(1);
            mcpController.writeValue(mcpCurrentValue);
            runOnUiThread(() -> showToast("MCP41010 初始化完成"));
        } catch (CH341LibException e) {
            runOnUiThread(() -> showToast("MCP41010 初始化失败: " + e.getMessage()));
        }
    });
}


// 打开/关闭设备时记得调用
if (CH341Manager.getInstance().openDevice(usbDevice)) {
    isDeviceOpen = true;
    this.usbDevice = usbDevice;
    sendMessage(OPEN_DEVICE);
    initializeAd9833();
    initializeMcp41010();
}



private void closeDevice() {
    if (!isDeviceOpen || this.usbDevice == null) {
        return;
    }
    CH341Manager.getInstance().closeDevice(usbDevice);
    isDeviceOpen = false;
    this.usbDevice = null;
    ad9833Controller.detach();
    mcpController.detach();
    sendMessage(CLOSE_DEVICE);
}


// onDestroy 清理
@Override
protected void onDestroy() {
    super.onDestroy();
    CH341Manager.getInstance().close(this);
    if (ad9833Controller != null) ad9833Controller.detach();
    if (mcpController != null) mcpController.detach();
    ioExec.shutdownNow();
}
