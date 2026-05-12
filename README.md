# BlueCarController 蓝牙小车控制器

Android 原生蓝牙 SPP 遥控 APP，用于通过 JDY-31 蓝牙模块控制电机驱动板。

## 功能

- 蓝牙 SPP 连接 JDY-31 模块
- 方向盘式控制面板：前进、后退、左转、右转、停止
- 按住方向键持续运动，松开自动停止
- 实时日志显示收发数据
- 深色主题 UI

## 控制协议

通过蓝牙串口发送单字节 ASCII 命令：

| 命令 | 功能 |
|------|------|
| `F`  | 前进 |
| `B`  | 后退 |
| `L`  | 左转 |
| `R`  | 右转 |
| `S`  | 停止 |

## 硬件连接

- **蓝牙模块**: JDY-31 (SPP, 115200 baud)
- **MCU**: STM32F103RCT6
- `JDY-31 TXD` → `STM32 PB11 (USART3_RX)`
- `JDY-31 RXD` → `STM32 PB10 (USART3_TX)`
- `VCC` → 3.3V/5V
- `GND` → GND

## 编译

1. 用 Android Studio 打开项目
2. Sync Gradle
3. Build → Generate Signed APK 或直接 Run

## 环境要求

- Android Studio Hedgehog (2023.1) 或更高
- Android SDK 34
- Min SDK 21 (Android 5.0)
