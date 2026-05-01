package com.yunshu.txtreader;

import android.content.Context;
import android.os.Environment;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 文件桥接接口 —— 让 HTML/JS 能读写手机文件系统
 * 文件统一存放在 Documents/TXTReader/ 目录
 */
public class FileBridge {

    private final Context context;

    // 固定文件夹：Documents/TXTReader/
    private static final String FOLDER_NAME = "TXTReader";

    public FileBridge(Context context) {
        this.context = context;
    }

    /** 获取工作目录 */
    private File getWorkDir() {
        File dir = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FOLDER_NAME
        );
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** 获取工作目录路径（给 JS 用） */
    @JavascriptInterface
    public String getWorkDirPath() {
        return getWorkDir().getAbsolutePath();
    }

    /** 列出所有 .txt 文件，返回 JSON 数组 */
    @JavascriptInterface
    public String listFiles() {
        File dir = getWorkDir();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".txt"));

        if (files == null || files.length == 0) {
            return "[]";
        }

        // 按修改时间倒序
        List<File> fileList = new ArrayList<>();
        Collections.addAll(fileList, files);
        Collections.sort(fileList, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < fileList.size(); i++) {
            File f = fileList.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escapeJson(f.getName()))
              .append("\",\"size\":").append(f.length())
              .append(",\"path\":\"").append(escapeJson(f.getAbsolutePath())).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    /** 读取文件内容 */
    @JavascriptInterface
    public String readFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return "__ERROR__文件不存在";

            // 安全检查：只允许读取 TXTReader 目录下的文件
            if (!file.getAbsolutePath().startsWith(getWorkDir().getAbsolutePath())) {
                return "__ERROR__无权访问该文件";
            }

            InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            char[] buffer = new char[8192];
            StringBuilder sb = new StringBuilder();
            int len;
            while ((len = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "__ERROR__" + e.getMessage();
        }
    }

    /** 保存文件（覆盖写入） */
    @JavascriptInterface
    public String saveFile(String filePath, String content) {
        try {
            File file = new File(filePath);

            // 安全检查：只允许写入 TXTReader 目录
            if (!file.getAbsolutePath().startsWith(getWorkDir().getAbsolutePath())) {
                return "__ERROR__无权写入该位置";
            }

            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write(content);
            writer.flush();
            writer.close();
            return "__OK__";
        } catch (Exception e) {
            return "__ERROR__" + e.getMessage();
        }
    }

    /** 新建文件 */
    @JavascriptInterface
    public String createFile(String fileName) {
        try {
            if (!fileName.toLowerCase().endsWith(".txt")) {
                fileName += ".txt";
            }
            File file = new File(getWorkDir(), fileName);
            if (file.exists()) {
                return "__ERROR__文件已存在";
            }
            // 创建空文件
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write("");
            writer.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            return "__ERROR__" + e.getMessage();
        }
    }

    /** 删除文件 */
    @JavascriptInterface
    public String deleteFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.getAbsolutePath().startsWith(getWorkDir().getAbsolutePath())) {
                return "__ERROR__无权删除该文件";
            }
            if (file.delete()) {
                return "__OK__";
            }
            return "__ERROR__删除失败";
        } catch (Exception e) {
            return "__ERROR__" + e.getMessage();
        }
    }

    /** 重命名文件 */
    @JavascriptInterface
    public String renameFile(String oldPath, String newName) {
        try {
            File oldFile = new File(oldPath);
            if (!oldFile.getAbsolutePath().startsWith(getWorkDir().getAbsolutePath())) {
                return "__ERROR__无权操作该文件";
            }
            if (!newName.toLowerCase().endsWith(".txt")) {
                newName += ".txt";
            }
            File newFile = new File(getWorkDir(), newName);
            if (newFile.exists()) {
                return "__ERROR__目标文件已存在";
            }
            if (oldFile.renameTo(newFile)) {
                return newFile.getAbsolutePath();
            }
            return "__ERROR__重命名失败";
        } catch (Exception e) {
            return "__ERROR__" + e.getMessage();
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
