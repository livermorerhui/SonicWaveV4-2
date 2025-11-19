const multer = require('multer');
const path = require('path');

// 使用 diskStorage，将文件临时存储在服务器硬盘上，而不是内存中
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    // 'uploads/' 是存储上传文件的目录
    cb(null, 'uploads/');
  },
  filename: function (req, file, cb) {
    // 创建一个唯一的文件名，避免重名覆盖
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, file.fieldname + '-' + uniqueSuffix + path.extname(file.originalname));
  }
});

const upload = multer({ storage: storage });

module.exports = upload;
