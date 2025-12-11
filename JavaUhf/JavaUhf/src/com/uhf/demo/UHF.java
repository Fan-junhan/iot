package com.uhf.demo;

import com.uhf.detailwith.InventoryDetailWith;
import com.uhf.linkage.Linkage;
import com.uhf.structures.InventoryArea;
import com.uhf.structures.RwData;
import com.uhf.utils.StringUtils;
import java.util.Map;

/**
 * UHF RFID 实验程序 - 实现标签盘点和读写操作
 * 实验要求：实现8位数字的读写操作，显示写入前后的数据对比
 */
public class UHF {

    // ============== 配置常量区 ==============
    private static final String COM_PORT = "COM4";          // 串口号（根据实际情况修改）
    private static final String ACCESS_PWD = "00000000";    // 访问密码（8位十六进制）
    private static final int READ_TIMEOUT_MS = 3000;        // 读取超时时间（毫秒）
    private static final int WRITE_TIMEOUT_MS = 500;        // 写入超时时间（毫秒）
    private static final int RETRIES = 5;                   // 失败重试次数
    
    // 写入测试数据（8位十六进制 = 4个字节 = 2个word）
    private static final String USER_WRITE_DATA = "12345678";   // 可修改为其他8位十六进制数

    // Bank 区域编号（SDK规定）
    private static final int BANK_EPC = 1;      // EPC区
    private static final int BANK_TID = 2;      // TID区
    private static final int BANK_USER = 3;     // USER区

    // 读取参数配置
    private static final int EPC_START_ADDR = 2;    // EPC起始地址（跳过CRC和PC）
    private static final int EPC_WORD_LEN = 1;      // EPC读取长度
    private static final int USER_START_ADDR = 0;   // USER起始地址
    private static final int USER_WORD_LEN = 2;     // USER读取长度（2个word = 8位十六进制）
    private static final int TID_START_ADDR = 2;    // TID起始地址
    private static final int TID_WORD_LEN = 1;      // TID读取长度

    // ============== 主函数 ==============
    @SuppressWarnings("static-access")
    public static void main(String[] args) {
        System.out.println("========== UHF RFID 实验开始 ==========\n");
        
        // 1. 初始化设备连接
        int i = Linkage.getInstance().initial(COM_PORT);
        if (i != 0) {
            System.out.println("❌ 连接失败！请检查：");
            System.out.println("   1. 串口号是否正确（当前：" + COM_PORT + "）");
            System.out.println("   2. 设备是否正常连接");
            System.out.println("   3. 驱动是否安装正确");
            return;
        }
        System.out.println("✓ 设备连接成功\n");

        // 2. 盘点操作流程
        System.out.println("========== 步骤1：标签盘点 ==========");
        getInventoryArea();     // 获取盘点区域
        setInventoryArea();     // 设置盘点区域
        startInventory();       // 开始盘点
        stopInventory();        // 停止盘点
        System.out.println();

        // 3. EPC区读取
        System.out.println("========== 步骤2：EPC区读取 ==========");
        epcReadSync();
        System.out.println();

        // 4. USER区读写测试（核心实验内容）
        System.out.println("========== 步骤3：USER区读写测试 ==========");
        System.out.println("【测试流程】写入前读取 → 写入数据 → 写入后读取 → 验证对比\n");
        
        String beforeWrite = userReadSync();        // 写入前读取
        boolean writeSuccess = userWriteSync(USER_WRITE_DATA);  // 写入数据
        String afterWrite = userReadSync();         // 写入后读取
        
        // 验证结果
        System.out.println("\n【验证结果】");
        System.out.println("  写入前数据: " + (beforeWrite.isEmpty() ? "空" : beforeWrite));
        System.out.println("  目标写入值: " + USER_WRITE_DATA);
        System.out.println("  写入后数据: " + (afterWrite.isEmpty() ? "空" : afterWrite));
        System.out.println("  写入操作: " + (writeSuccess ? "✓ 成功" : "❌ 失败"));
        System.out.println("  数据验证: " + (USER_WRITE_DATA.equalsIgnoreCase(afterWrite) ? "✓ 通过" : "❌ 不匹配"));
        System.out.println();

        // 5. TID区读取
        System.out.println("========== 步骤4：TID区读取 ==========");
        tidReadSync();
        System.out.println();

        // 6. 断开连接
        Linkage.getInstance().deinitRFID();
        System.out.println("========== 实验结束，设备已断开 ==========");
    }

    // ============== EPC区读取函数 ==============
    /**
     * EPC区同步读取
     * 参数说明：readTagSync(密码, Bank区号, 起始地址, 长度, 超时时间, 数据对象)
     */
    public static void epcReadSync() {
        byte[] password = StringUtils.stringToByte(ACCESS_PWD);
        RwData rwData = new RwData();

        // 添加重试机制，提高成功率
        for (int retry = 0; retry < RETRIES; retry++) {
            int status = Linkage.getInstance().readTagSync(
                    password,           // 访问密码
                    BANK_EPC,          // Bank区号：1=EPC
                    EPC_START_ADDR,    // 起始地址：2（跳过CRC和PC）
                    EPC_WORD_LEN,      // 读取长度：1个word
                    READ_TIMEOUT_MS,   // 超时时间：3000ms
                    rwData             // 返回数据对象
            );

            // 判断是否读取成功
            if (status == 0 && rwData.status == 0) {
                String result = (rwData.rwDataLen > 0) 
                        ? StringUtils.byteToHexString(rwData.rwData, rwData.rwDataLen) : "空";
                String epc = (rwData.epcLen > 0) 
                        ? StringUtils.byteToHexString(rwData.epc, rwData.epcLen) : "空";
                
                System.out.println("  读取数据: " + result);
                System.out.println("  EPC码: " + epc);
                System.out.println("  ✓ EPC读取成功");
                return;
            }
            
            if (retry < RETRIES - 1) {
                System.out.println("  第" + (retry + 1) + "次读取失败，重试中...");
            }
        }
        System.out.println("  ❌ EPC读取失败（已重试" + RETRIES + "次）");
    }

    // ============== USER区读取函数 ==============
    /**
     * USER区同步读取（核心函数1）
     * 实验要求：起始地址=0，读取长度=2（8位十六进制）
     */
    public static String userReadSync() {
        RwData rwData = new RwData();
        byte[] password = StringUtils.stringToByte(ACCESS_PWD);

        // 添加重试机制
        for (int retry = 0; retry < RETRIES; retry++) {
            int status = Linkage.getInstance().readTagSync(
                    password,           // 访问密码
                    BANK_USER,         // Bank区号：3=USER
                    USER_START_ADDR,   // 起始地址：0（实验要求）
                    USER_WORD_LEN,     // 读取长度：2个word（8位十六进制）
                    READ_TIMEOUT_MS,   // 超时时间
                    rwData             // 返回数据对象
            );

            if (status == 0 && rwData.status == 0) {
                String result = (rwData.rwDataLen > 0) 
                        ? StringUtils.byteToHexString(rwData.rwData, rwData.rwDataLen) : "";
                String epc = (rwData.epcLen > 0) 
                        ? StringUtils.byteToHexString(rwData.epc, rwData.epcLen) : "空";
                
                System.out.println("  USER数据: " + (result.isEmpty() ? "空" : result));
                System.out.println("  EPC码: " + epc);
                System.out.println("  ✓ USER读取成功");
                return result;
            }
            
            if (retry < RETRIES - 1) {
                System.out.println("  第" + (retry + 1) + "次读取失败，重试中...");
            }
        }
        System.out.println("  ❌ USER读取失败（已重试" + RETRIES + "次）");
        return "";
    }

    // ============== USER区写入函数 ==============
    /**
     * USER区同步写入（核心函数2）
     * 实验要求：起始地址=0，写入8位十六进制数据
     * @param hexData 要写入的十六进制字符串（8位）
     * @return 是否写入成功
     */
    public static boolean userWriteSync(String hexData) {
        byte[] password = StringUtils.stringToByte(ACCESS_PWD);
        byte[] writeData = StringUtils.stringToByte(hexData);
        RwData rwData = new RwData();

        // 添加有限次重试
        for (int retry = 0; retry < RETRIES; retry++) {
            int status = Linkage.getInstance().writeTagSync(
                    password,           // 访问密码
                    BANK_USER,         // Bank区号：3=USER
                    USER_START_ADDR,   // 起始地址：0（实验要求）
                    USER_WORD_LEN,     // 写入长度：2个word
                    writeData,         // 写入数据
                    WRITE_TIMEOUT_MS,  // 超时时间
                    rwData             // 返回数据对象
            );

            if (status == 0 && rwData.status == 0) {
                String epc = (rwData.epcLen > 0) 
                        ? StringUtils.byteToHexString(rwData.epc, rwData.epcLen) : "空";
                
                System.out.println("  写入数据: " + hexData);
                System.out.println("  EPC码: " + epc);
                System.out.println("  ✓ USER写入成功");
                return true;
            }
            
            if (retry < RETRIES - 1) {
                System.out.println("  第" + (retry + 1) + "次写入失败，重试中...");
            }
        }
        System.out.println("  ❌ USER写入失败（已重试" + RETRIES + "次）");
        return false;
    }

    // ============== TID区读取函数 ==============
    /**
     * TID区同步读取（核心函数3）
     * TID是标签唯一标识，只读不可写
     */
    public static void tidReadSync() {
        RwData rwData = new RwData();
        byte[] password = StringUtils.stringToByte(ACCESS_PWD);

        // 添加重试机制
        for (int retry = 0; retry < RETRIES; retry++) {
            int status = Linkage.getInstance().readTagSync(
                    password,           // 访问密码
                    BANK_TID,          // Bank区号：2=TID
                    TID_START_ADDR,    // 起始地址：2
                    TID_WORD_LEN,      // 读取长度：1个word
                    READ_TIMEOUT_MS,   // 超时时间
                    rwData             // 返回数据对象
            );

            if (status == 0 && rwData.status == 0) {
                String result = (rwData.rwDataLen > 0) 
                        ? StringUtils.byteToHexString(rwData.rwData, rwData.rwDataLen) : "空";
                String epc = (rwData.epcLen > 0) 
                        ? StringUtils.byteToHexString(rwData.epc, rwData.epcLen) : "空";
                
                System.out.println("  TID数据: " + result);
                System.out.println("  EPC码: " + epc);
                System.out.println("  ✓ TID读取成功");
                return;
            }
            
            if (retry < RETRIES - 1) {
                System.out.println("  第" + (retry + 1) + "次读取失败，重试中...");
            }
        }
        System.out.println("  ❌ TID读取失败（已重试" + RETRIES + "次）");
    }

    // ============== 盘点相关函数 ==============
    
    /**
     * 开始盘点标签
     * 注意：盘点和读写不能同时进行！
     */
    public static void startInventory() {
        InventoryArea inventory = new InventoryArea();
        inventory.setValue(2, 0, 6);  // 设置盘点区域：EPC+USER
        Linkage.getInstance().setInventoryArea(inventory);
        
        InventoryDetailWith.tagCount = 0;
        Linkage.getInstance().startInventory(2, 0);
        InventoryDetailWith.startTime = System.currentTimeMillis();

        // 等待盘点到100个标签或超时
        while (InventoryDetailWith.totalCount < 100) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        stopInventory();  // 必须停止盘点才能进行读写操作

        // 显示盘点结果
        for (Map<String, Object> _map : InventoryDetailWith.list) {
            System.out.println("  标签信息: " + _map);
            System.out.println("    天线号: " + _map.get("antennaPort"));
            System.out.println("    EPC码: " + _map.get("epc"));
            System.out.println("    扩展数据: " + _map.get("externalData"));
            System.out.println("    读取次数: " + _map.get("count"));
            System.out.println("    信号强度: " + _map.get("rssi"));
        }

        // 统计信息
        long endTime = System.currentTimeMillis();
        double rate = Math.ceil((InventoryDetailWith.tagCount * 1.0) * 1000 
                / (endTime - InventoryDetailWith.startTime));
        long totalTime = endTime - InventoryDetailWith.startTime;
        String timeStr = StringUtils.getTimeFromMillisecond(totalTime);
        int tagCount = InventoryDetailWith.list.size();

        System.out.println("\n  【盘点统计】");
        System.out.println("  盘点速率: " + rate + " 标签/秒");
        System.out.println("  盘点时间: " + (tagCount != 0 ? timeStr : "0时0分0秒0毫秒"));
        System.out.println("  标签数量: " + tagCount);
    }

    /**
     * 停止盘点
     */
    public static void stopInventory() {
        Linkage.getInstance().stopInventory();
        System.out.println("  ✓ 盘点已停止");
    }

    /**
     * 获取盘点区域配置
     */
    public static void getInventoryArea() {
        InventoryArea inventoryArea = new InventoryArea();
        int status = Linkage.getInstance().getInventoryArea(inventoryArea);
        
        if (status == 0) {
            System.out.println("  区域代码: " + inventoryArea.area);
            System.out.println("  起始地址: " + inventoryArea.startAddr);
            System.out.println("  字长度: " + inventoryArea.wordLen);
            System.out.println("  ✓ 获取盘点区域成功");
            return;
        }
        System.out.println("  ❌ 获取盘点区域失败");
    }

    /**
     * 设置盘点区域
     */
    public static void setInventoryArea() {
        InventoryArea inventoryArea = new InventoryArea();
        inventoryArea.setValue(2, 0, 6);  // 2表示EPC+USER区域
        int status = Linkage.getInstance().setInventoryArea(inventoryArea);
        
        if (status == 0) {
            System.out.println("  ✓ 设置盘点区域成功（EPC+USER）");
            return;
        }
        System.out.println("  ❌ 设置盘点区域失败");
    }
}