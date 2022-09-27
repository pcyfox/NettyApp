package com.pcommon.lib_common.config;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Keep;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Keep
public class DeskConfig {

    @Expose(serialize = false, deserialize = false)
    private static final String TAG = "DeskConfig";

    @Expose(serialize = false, deserialize = false)
    private static DeskConfig instance;

    @Expose(serialize = false, deserialize = false)
    public String DESK_CONFIG_PATH;

    @Expose(serialize = false, deserialize = false)
    public String DESK_NUMBER_MAPPING_DATA_PATH;

    @Expose
    private String deskNumber = "-1";

    @Expose
    private String deskLine;

    @Expose
    private String deskColumn;

    @Expose
    private String deviceId;

    @Expose
    private String location;

    @Expose
    private String luaUrl;

    @Expose(serialize = false, deserialize = false)
    private DeskConfig localConfig;

    @Expose(serialize = false, deserialize = false)
    public final DeskNumberMappingData mappingData = new DeskNumberMappingData();

    @Expose(serialize = false, deserialize = false)
    private final String configFileName = "DeskConfig.conf";

    @Expose(serialize = false, deserialize = false)
    private final String mappingFileName = "DeskNumberMapping.conf";

    public static DeskConfig getInstance() {
        if (instance == null) {
            instance = new DeskConfig();
        }
        return instance;
    }

    private DeskConfig() {
        String rootDir= Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
        DESK_CONFIG_PATH = rootDir + configFileName;
        DESK_NUMBER_MAPPING_DATA_PATH = rootDir + mappingFileName;
    }

    //为了兼容老版本
    private void copyOldConfigFileToNewDir() {
//        String oldRootDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
//        String newRootDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/config/";
//
//        File newDir = new File(newRootDir);
//        if (!newDir.exists()) {
//            newDir.mkdirs();
//        }
//
//        File oldConfigFile = new File(oldRootDir + configFileName);
//        if (oldConfigFile.exists()) {
//            File newConfigFile = new File(newRootDir + configFileName);
//            if (!newConfigFile.exists() || newConfigFile.lastModified() < oldConfigFile.lastModified()) {
//                boolean ret = copyFileUsingStream(oldConfigFile, newConfigFile);
//                if (ret) {
//                    oldConfigFile.delete();
//                }
//            }
//        }
//
//        File oldMappingFile = new File(oldRootDir + mappingFileName);
//        if (oldMappingFile.exists()) {
//            File newMappingFile = new File(newRootDir + mappingFileName);
//            if (!newMappingFile.exists() || newMappingFile.lastModified() < oldMappingFile.lastModified()) {
//                boolean ret = copyFileUsingStream(oldMappingFile, newMappingFile);
//                if (ret) {
//                    oldConfigFile.delete();
//                }
//            }
//        }
//        DESK_CONFIG_PATH = newRootDir + configFileName;
//        DESK_NUMBER_MAPPING_DATA_PATH = newRootDir + mappingFileName;
    }

    private boolean copyFileUsingStream(File source, File dest) {
        Log.d(TAG, "copyFileUsingStream() called with: source = [" + source + "], dest = [" + dest + "]");
        try (InputStream is = new FileInputStream(source); OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }


    //检测桌号合法性
    public static boolean isDeskNumberRight(String deskNumber) {
        return !TextUtils.isEmpty(deskNumber) && !"-1".equals(deskNumber);
    }

    public void updateLocalData() {
        //copyOldConfigFileToNewDir();
        copyLocationConfig();
        loadLocalDeskNumberMappingData();
    }


    private void copyLocationConfig() {
        localConfig = getLocalConfig(true);
        if (localConfig != null) {
            if (!TextUtils.isEmpty(localConfig.deviceId)) {
                this.deviceId = localConfig.deviceId;
            }
            if (!TextUtils.isEmpty(localConfig.deskNumber)) {
                this.deskNumber = localConfig.deskNumber;
            }
            if (!TextUtils.isEmpty(localConfig.location)) {
                this.location = localConfig.location;
            }
            if (!TextUtils.isEmpty(localConfig.luaUrl)) {
                this.luaUrl = localConfig.luaUrl;
            }

            if (!TextUtils.isEmpty(localConfig.deskColumn)) {
                this.deskColumn = localConfig.deskColumn;
            }

            if (!TextUtils.isEmpty(localConfig.deskLine)) {
                this.deskLine = localConfig.deskLine;
            }
        }
    }


    public DeskConfig getLocalConfig(boolean isForceUpdate) {
        if (localConfig == null || isForceUpdate) {
            localConfig = loadDeskConfig();
        }
        return localConfig;
    }

    public String getDeviceId() {
        if (TextUtils.isEmpty(deviceId)) {
            localConfig = getLocalConfig(false);
            if (localConfig != null && !TextUtils.isEmpty(localConfig.deviceId)) {
                deviceId = localConfig.deviceId;
            }
        }
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        if (!TextUtils.isEmpty(getDeviceId())) {
            return;
        }
        copyLocationConfig();
        this.deviceId = deviceId;
        saveDeskConfig(this);
    }

    public String getDeskNumber() {
        return getDeskNumber(false);
    }

    public String getDeskNumber(boolean forceUpdate) {
        localConfig = getLocalConfig(forceUpdate);
        if (localConfig != null) {
            deskNumber = localConfig.deskNumber;
        }
        boolean isOk = tryParseDeskNumberToXY(deskNumber);
        if (isOk || (!TextUtils.isEmpty(deskLine) && !TextUtils.isEmpty(deskColumn))) {
            deskNumber = findDeskNumberFormMappingFile(deskLine, deskColumn);
        }
        return deskNumber;
    }


    private void loadLocalDeskNumberMappingData() {
        Map<String, String> data = new HashMap<>();
        data = loadDeskData(data.getClass(), DESK_NUMBER_MAPPING_DATA_PATH);
        mappingData.setMappingData(data);
    }

    /**
     * 从映射文件中查找对应的桌号
     *
     * @param deskLine
     * @param deskColumn
     * @return
     */
    public String findDeskNumberFormMappingFile(String deskLine, String deskColumn) {
        loadLocalDeskNumberMappingData();
        return mappingData.findDeskNumber(deskLine, deskColumn);
    }


    public void setDeskNumber(String deskNumber) {
        if (isDeskNumberRight(deskNumber)) {
            //有可能此时本地配置文件已被其它APP修改了
            copyLocationConfig();
            this.deskNumber = deskNumber;
            if (deskNumber.contains("-")) {
                tryParseDeskNumberToXY(deskNumber);
            } else {
                String rawDeskNumber = mappingData.findDeskLineAndColumn(deskNumber);
                boolean isOk = tryParseDeskNumberToXY(rawDeskNumber);
                if (!isOk) {
                    deskColumn = deskLine = "";
                }
            }
            saveDeskConfig(this);
        }
    }


    private boolean tryParseDeskNumberToXY(String deskNumber) {
        if (TextUtils.isEmpty(deskNumber)) {
            return false;
        }
        if (deskNumber.contains("-")) {
            String[] data = deskNumber.split("-");
            if (data.length == 2) {
                deskLine = data[0];
                deskColumn = data[1];
                return true;
            }
        }
        return false;
    }

    public String getLocation() {
        if (TextUtils.isEmpty(location)) {
            localConfig = getLocalConfig(false);
            if (localConfig != null) {
                location = localConfig.location;
            }
        }
        return location;
    }

    public void setLocation(String location) {
        if (!TextUtils.isEmpty(location)) {
            copyLocationConfig();
            this.location = location;
            saveDeskConfig(this);
        }
    }

    public String getLuaUrl() {
        if (TextUtils.isEmpty(luaUrl)) {
            localConfig = getLocalConfig(false);
            if (localConfig != null) {
                luaUrl = localConfig.luaUrl;
            }
        }
        return luaUrl;
    }

    public void setLuaUrl(String luaUrl) {
        if (!TextUtils.isEmpty(luaUrl)) {
            copyLocationConfig();
            this.luaUrl = luaUrl;
            saveDeskConfig(this);
        }
    }

    public String getDeskColumn() {
        return deskColumn;
    }

    public String getDeskLine() {
        return deskLine;
    }


    private DeskConfig loadDeskConfig() {
        return loadDeskData(DeskConfig.class, DESK_CONFIG_PATH);
    }

    private void saveDeskConfig(DeskConfig deskConfig) {
        saveDataToDesk(deskConfig, DESK_CONFIG_PATH);
    }

    public static <T> void saveDataToDesk(T data, String dataPath) {
        File file = new File(dataPath);
        if (file.exists() && file.canRead()) {
            file.delete();
        }
        FileWriter fwriter = null;
        try {
            fwriter = new FileWriter(dataPath, false);
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.excludeFieldsWithoutExposeAnnotation();
            Gson gson = gsonBuilder.create();
            fwriter.write(gson.toJson(data));
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (fwriter != null) {
                    fwriter.flush();
                    fwriter.close();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

    }

    public static <T> T loadDeskData(Class<T> clazz, String dataPath) {
        InputStream in = null;
        try {
            File file = new File(dataPath);
            if (file == null || !file.exists() || !file.canRead()) {
                return null;
            }
            in = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            if (!TextUtils.isEmpty(jsonString)) {
                GsonBuilder gsonBuilder = new GsonBuilder();
                gsonBuilder.excludeFieldsWithoutExposeAnnotation();
                Gson gson = gsonBuilder.create();
                return gson.fromJson(jsonString.toString(), clazz);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }


    @Override
    public String toString() {
        return "DeskConfig{" +
                "DESK_CONFIG_PATH='" + DESK_CONFIG_PATH + '\'' +
                ", DESK_NUMBER_MAPPING_DATA_PATH='" + DESK_NUMBER_MAPPING_DATA_PATH + '\'' +
                ", deskNumber='" + deskNumber + '\'' +
                ", deskLine='" + deskLine + '\'' +
                ", deskColumn='" + deskColumn + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", location='" + location + '\'' +
                ", host='" + luaUrl + '\'' +
                ", localConfig=" + localConfig +
                '}';
    }

    public static class DeskNumberMappingData {
        private Map<String, String> mappingData;

        public Map<String, String> getMappingData() {
            return mappingData;
        }

        public void setMappingData(Map<String, String> mappingData) {
            Log.d(TAG, "setMappingData() called with: mappingData = [" + mappingData + "]");
            this.mappingData = mappingData;
        }

        public String findDeskLineAndColumn(String deskNumber) {
            if (mappingData == null || mappingData.isEmpty() || TextUtils.isEmpty(deskNumber)) {
                return null;
            }
            Set<Map.Entry<String, String>> entrySet = mappingData.entrySet();
            for (Map.Entry<String, String> e : entrySet) {
                if (deskNumber.equals(e.getValue())) {
                    return e.getKey();
                }
            }
            return null;
        }


        public String findDeskNumber(String deskLine, String deskColumn) {
            String defNumber = deskLine + "-" + deskColumn;
            if (mappingData != null) {
                String deskNumber = mappingData.get(defNumber);
                if (!TextUtils.isEmpty(deskNumber)) {
                    defNumber = deskNumber;
                }
            }
            return defNumber;
        }

        @Override
        public String toString() {
            return "DeskNumberMappingData{" +
                    "mappingData=" + mappingData +
                    '}';
        }
    }


}
