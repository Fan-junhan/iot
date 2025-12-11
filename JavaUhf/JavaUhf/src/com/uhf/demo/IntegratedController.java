package com.integrated.controller;

import com.uhf.detailwith.InventoryDetailWith;
import com.uhf.linkage.Linkage;
import com.yang.serialport.exception.*;
import com.yang.serialport.manage.SerialPortManager;
import com.yang.serialport.utils.ByteUtils;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * 集成控制器：管理RFID读卡器和WSN传感器系统
 * Integrated Controller: Manages RFID reader and WSN sensor system
 */
public class IntegratedController {
    
    // WSN传感器串口
    private SerialPort wsnSerialPort;
    
    // 最新光照强度值（线程安全）
    private volatile double currentIllumination = 0.0;
    
    // 光照阈值（小于此值需要开灯）
    private static final double ILLUMINATION_THRESHOLD = 100.0;
    
    // 标签ID与LED颜色映射表
    private Map<String, String> tagColorMap;
    
    // 控制命令
    private static final String RED_LED_ON = "FFFFB6250100FEFE";   // 红灯亮，绿灯灭
    private static final String GREEN_LED_ON = "FFFFB6250001FEFE"; // 绿灯亮，红灯灭
    private static final String LED_OFF = "FFFFB6240000FEFE";      // 关闭LED
    
    private boolean isRunning = false;
    
    public IntegratedController() {
        // 初始化标签-颜色映射表
        tagColorMap = new HashMap<>();
        initTagColorMapping();
    }
    
    /**
     * 初始化标签ID与LED颜色的映射关系
     * 可以根据实际需求修改映射规则
     */
    private void initTagColorMapping() {
        // 示例：根据标签ID的特征决定颜色
        // 这里使用简单规则：可以根据实际标签ID进行配置
        
        // 方式1：预定义具体标签ID
        // tagColorMap.put("E2001234567890123456", "RED");
        // tagColorMap.put("E2009876543210987654", "GREEN");
        
        // 方式2：在运行时根据ID特征判断（见getColorForTag方法）
    }
    
    /**
     * 根据标签ID决定LED颜色
     * @param epcId 标签EPC ID
     * @return "RED" 或 "GREEN"
     */
    private String getColorForTag(String epcId) {
        // 优先查找预定义映射
        if (tagColorMap.containsKey(epcId)) {
            return tagColorMap.get(epcId);
        }
        
        // 默认规则：根据ID的数值特征判断
        // 规则1：如果ID最后一位是偶数，用红灯；奇数用绿灯
        try {
            char lastChar = epcId.charAt(epcId.length() - 1);
            int lastDigit = Character.digit(lastChar, 16);
            return (lastDigit % 2 == 0) ? "RED" : "GREEN";
        } catch (Exception e) {
            // 默认返回红灯
            return "RED";
        }
        
        // 规则2：根据ID范围划分（示例）
        // if (epcId.compareTo("E200500000000000000") < 0) {
        //     return "RED";
        // } else {
        //     return "GREEN";
        // }
    }
    
    /**
     * 启动集成系统
     * @param rfidComPort RFID读卡器COM端口（如"COM4"）
     * @param wsnComPort WSN传感器COM端口（如"COM3"）
     * @param baudrate WSN串口波特率
     */
    public void start(String rfidComPort, String wsnComPort, int baudrate) {
        System.out.println("=== 启动集成控制系统 ===");
        
        // 1. 初始化WSN传感器串口
        if (!initWsnSerialPort(wsnComPort, baudrate)) {
            System.err.println("WSN传感器初始化失败！");
            return;
        }
        System.out.println("✓ WSN传感器初始化成功");
        
        // 2. 初始化RFID读卡器
        int rfidStatus = Linkage.getInstance().initial(rfidComPort);
        if (rfidStatus != 0) {
            System.err.println("RFID读卡器初始化失败！");
            closeWsnSerialPort();
            return;
        }
        System.out.println("✓ RFID读卡器初始化成功");
        
        // 3. 启动RFID标签监听
        isRunning = true;
        startRfidMonitoring();
        
        System.out.println("=== 系统运行中，等待标签识别... ===\n");
    }
    
    /**
     * 初始化WSN传感器串口
     */
    private boolean initWsnSerialPort(String portName, int baudrate) {
        try {
            wsnSerialPort = SerialPortManager.openPort(portName, baudrate);
            if (wsnSerialPort != null) {
                // 添加串口监听器
                SerialPortManager.addListener(wsnSerialPort, new WsnSerialListener());
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * 启动RFID标签监听
     */
    private void startRfidMonitoring() {
        new Thread(() -> {
            while (isRunning) {
                try {
                    // 清空之前的盘点数据
                    InventoryDetailWith.list.clear();
                    InventoryDetailWith.tagCount = 0;
                    InventoryDetailWith.totalCount = 0;
                    
                    // 开始盘点
                    Linkage.getInstance().startInventory(2, 0);
                    InventoryDetailWith.startTime = System.currentTimeMillis();
                    
                    // 等待盘点一段时间（500ms）
                    Thread.sleep(500);
                    
                    // 停止盘点
                    Linkage.getInstance().stopInventory();
                    
                    // 处理盘点到的标签
                    if (!InventoryDetailWith.list.isEmpty()) {
                        processDetectedTags();
                    }
                    
                    // 短暂延迟后继续下一轮盘点
                    Thread.sleep(1000);
                    
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    
    /**
     * 处理检测到的RFID标签
     */
    private void processDetectedTags() {
        for (Map<String, Object> tagData : InventoryDetailWith.list) {
            String epcId = (String) tagData.get("epc");
            
            if (epcId != null && !epcId.isEmpty()) {
                System.out.println("\n>>> 检测到RFID标签 <<<");
                System.out.println("标签ID (EPC): " + epcId);
                System.out.println("天线端口: " + tagData.get("antennaPort"));
                System.out.println("RSSI信号强度: " + tagData.get("rssi"));
                System.out.println("读取次数: " + tagData.get("count"));
                
                // 执行LED控制逻辑
                controlLedBasedOnTag(epcId);
            }
        }
    }
    
    /**
     * 根据标签ID和光照强度控制LED
     */
    private void controlLedBasedOnTag(String epcId) {
        System.out.println("\n--- LED控制决策 ---");
        System.out.println("当前光照强度: " + String.format("%.2f", currentIllumination) + " lux");
        System.out.println("光照阈值: " + ILLUMINATION_THRESHOLD + " lux");
        
        if (currentIllumination < ILLUMINATION_THRESHOLD) {
            // 光照不足，需要开灯
            String color = getColorForTag(epcId);
            System.out.println("判断结果: 光照不足，需要开灯");
            System.out.println("标签映射颜色: " + color);
            
            String command = color.equals("RED") ? RED_LED_ON : GREEN_LED_ON;
            sendLedCommand(command, color + "灯");
            
        } else {
            // 光照充足，关闭LED
            System.out.println("判断结果: 光照充足，关闭LED");
            sendLedCommand(LED_OFF, "关闭LED");
        }
        System.out.println("-------------------\n");
    }
    
    /**
     * 发送LED控制命令
     */
    private void sendLedCommand(String hexCommand, String description) {
        try {
            byte[] command = ByteUtils.hexStr2Byte(hexCommand);
            SerialPortManager.sendToPort(wsnSerialPort, command);
            System.out.println("✓ 已发送命令: " + description + " (" + hexCommand + ")");
        } catch (Exception e) {
            System.err.println("✗ 发送命令失败: " + e.getMessage());
        }
    }
    
    /**
     * WSN传感器串口监听器
     */
    private class WsnSerialListener implements SerialPortEventListener {
        @Override
        public void serialEvent(SerialPortEvent event) {
            if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
                try {
                    byte[] data = SerialPortManager.readFromPort(wsnSerialPort);
                    
                    // 解析光照数据（数据长度为58字节）
                    if (ByteUtils.byteArrayToHexString(data, true).length() == 58) {
                        currentIllumination = parseIllumination(data);
                        // 可选：打印传感器数据
                        // System.out.println("更新光照强度: " + currentIllumination + " lux");
                    }
                } catch (Exception e) {
                    // 静默处理，避免过多日志
                }
            }
        }
        
        /**
         * 解析光照强度
         */
        private double parseIllumination(byte[] data) {
            return (double) ((data[22] + (data[23] << 8)) / Math.pow(2, 16) * 16000);
        }
    }
    
    /**
     * 停止系统
     */
    public void stop() {
        System.out.println("\n=== 停止集成控制系统 ===");
        isRunning = false;
        
        // 停止RFID盘点
        Linkage.getInstance().stopInventory();
        Linkage.getInstance().deinitRFID();
        
        // 关闭WSN串口
        closeWsnSerialPort();
        
        System.out.println("系统已停止");
    }
    
    /**
     * 关闭WSN串口
     */
    private void closeWsnSerialPort() {
        if (wsnSerialPort != null) {
            SerialPortManager.closePort(wsnSerialPort);
            wsnSerialPort = null;
        }
    }
    
    /**
     * 手动添加标签-颜色映射
     */
    public void addTagColorMapping(String epcId, String color) {
        if (color.equalsIgnoreCase("RED") || color.equalsIgnoreCase("GREEN")) {
            tagColorMap.put(epcId, color.toUpperCase());
            System.out.println("已添加映射: " + epcId + " -> " + color);
        } else {
            System.err.println("颜色必须是 RED 或 GREEN");
        }
    }
    
    /**
     * 主函数示例
     */
    public static void main(String[] args) {
        IntegratedController controller = new IntegratedController();
        
        // 配置COM端口（根据实际情况修改）
        String rfidComPort = "COM4";  // RFID读卡器端口
        String wsnComPort = "COM3";   // WSN传感器端口
        int wsnBaudrate = 9600;        // WSN波特率
        
        // 可选：添加自定义标签映射
        // controller.addTagColorMapping("E2001234567890123456", "RED");
        // controller.addTagColorMapping("E2009876543210987654", "GREEN");
        
        // 启动系统
        controller.start(rfidComPort, wsnComPort, wsnBaudrate);
        
        // 运行一段时间后可以调用 controller.stop() 停止
        // 这里让主线程保持运行
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            controller.stop();
        }
    }
}