package com.immomo.wink.util;

import com.immomo.wink.Settings;

import org.jetbrains.annotations.NotNull;

public class PathUtils {

    @NotNull
    public static String getVersionPath(String deviceId) {
        String patch = "/sdcard/Android/data/" + Settings.env.debugPackageName;
        //没啥用
//        Utils.ShellResult result = Utils.runShells("source ~/.bash_profile\nadb -s " + deviceId + " shell ls " + patch);
        boolean noPermission = false;
        Utils.ShellResult result = Utils.runShells(Utils.ShellOutput.NONE, "source ~/.bash_profile\nadb -s " + deviceId + " shell mkdir -p " + patch);
        for (String error : result.getErrorResult()) {
            if (error.contains("Permission denied")) {
                noPermission = true; // 没文件权限
                break;
            }
        }
        if (noPermission) {
            patch = "/sdcard/" + Settings.NAME + "/patch_version/";
        } else {
            patch = "/sdcard/Android/data/" + Settings.env.debugPackageName + "/patch_version/";
        }

        result = Utils.runShells(Utils.ShellOutput.NONE, "source ~/.bash_profile",
                "adb -s " + deviceId + " shell mkdir -p " + patch);//mkdir -p 递归创建
        //没啥用
//        result = Utils.runShells("source ~/.bash_profile","adb -s " + deviceId + " shell ls " + patch);
        if (result.getErrorResult().size() > 0) {
            result.getErrorResult().stream().forEach(s -> {
                WinkLog.throwAssert("\t\t" + s);
            });
            WinkLog.throwAssert("创建 apk 基准版本文件失败 " + Settings.data.patchPath);
        }
        return patch;
    }

}
