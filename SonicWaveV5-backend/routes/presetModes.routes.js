const express = require('express');
const router = express.Router();
const presetModesController = require('../controllers/presetModes.controller');

router.post('/start', presetModesController.startPresetModeRun);
router.put('/stop/:id', presetModesController.stopPresetModeRun);

module.exports = router;
