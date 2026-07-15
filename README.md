# 📍 位置闹钟 (LocationAlarm)

一个基于位置的智能提醒 Android 应用 — 当你到达指定位置的指定范围内时，自动触发提醒通知。

## 功能特性

### 核心功能
- **位置触发提醒**：设置目标位置 + 范围半径（米），到达范围内自动推送通知
- **地址搜索**：输入地址名称（如"天安门广场"），自动解析为经纬度坐标
- **多闹钟管理**：支持创建多个位置闹钟，独立启用/禁用
- **触发历史记录**：每次触发自动记录时间、位置、距离，支持查看和清除
- **分类标签**：为闹钟添加自定义标签（如"工作"、"生活"），便于分类管理

### 技术特性
- **Kotlin 原生开发**，最低支持 Android 10 (API 29)
- **前台服务 + FusedLocationProviderClient** 实现持续后台定位
- **Room 数据库** 持久化闹钟和历史数据
- **Material Design 3** UI 风格
- **开机自启**，设备重启后自动恢复监控
- **ViewBinding** + **LiveData** + **ViewModel** 架构

## 项目结构

```
LocationAlarm/
├── settings.gradle.kts              # 项目设置
├── build.gradle.kts                 # 根构建文件
├── gradle.properties                # Gradle 配置
├── gradle/wrapper/
│   └── gradle-wrapper.properties    # Gradle Wrapper 配置
├── app/
│   ├── build.gradle.kts             # App 模块构建文件
│   ├── proguard-rules.pro           # ProGuard 规则
│   └── src/main/
│       ├── AndroidManifest.xml      # 清单文件
│       ├── java/com/example/locationalarm/
│       │   ├── LocationAlarmApp.kt          # Application 类
│       │   ├── data/                        # 数据层
│       │   │   ├── Alarm.kt                 # 闹钟实体
│       │   │   ├── AlarmHistory.kt          # 历史记录实体
│       │   │   ├── AlarmDao.kt              # 闹钟 DAO
│       │   │   ├── AlarmHistoryDao.kt       # 历史 DAO
│       │   │   ├── AlarmDatabase.kt         # Room 数据库
│       │   │   └── AlarmRepository.kt       # 数据仓库
│       │   ├── service/                     # 服务层
│       │   │   ├── LocationAlarmService.kt  # 前台定位服务
│       │   │   ├── NotificationHelper.kt    # 通知管理
│       │   │   └── BootReceiver.kt          # 开机自启
│       │   └── ui/                          # UI 层
│       │       ├── MainActivity.kt          # 主界面（闹钟列表）
│       │       ├── AddEditAlarmActivity.kt  # 添加/编辑闹钟
│       │       ├── HistoryActivity.kt       # 历史记录界面
│       │       ├── AlarmAdapter.kt          # 闹钟列表适配器
│       │       ├── HistoryAdapter.kt        # 历史记录适配器
│       │       └── AlarmViewModel.kt        # ViewModel
│       └── res/                             # 资源文件
│           ├── layout/                      # 布局
│           │   ├── activity_main.xml
│           │   ├── activity_add_edit_alarm.xml
│           │   ├── activity_history.xml
│           │   ├── item_alarm.xml
│           │   └── item_history.xml
│           ├── values/                      # 字符串、颜色、主题
│           │   ├── strings.xml
│           │   ├── colors.xml
│           │   └── themes.xml
│           ├── values-night/               # 夜间模式主题
│           ├── drawable/                    # 矢量图标
│           ├── mipmap-anydpi-v26/          # 自适应图标
│           └── xml/
│               └── backup_rules.xml        # 备份规则
```

## 编译运行

### 环境要求
- Android Studio Hedgehog (2023.1) 或更高版本
- JDK 17
- Android SDK 34
- Gradle 8.2

### 步骤
1. 将 `LocationAlarm` 文件夹复制到你的开发目录
2. 打开 Android Studio → File → Open → 选择 `LocationAlarm` 文件夹
3. 等待 Gradle 同步完成
4. 连接 Android 设备或启动模拟器
5. 点击 Run 按钮

### 注意事项
- Google Play Services 必须在设备上可用（大多数 Android 设备已预装）
- Geocoder 需要网络连接才能将地址解析为坐标

## 权限说明

| 权限 | 用途 |
|------|------|
| `ACCESS_FINE_LOCATION` | 获取精确位置（GPS） |
| `ACCESS_COARSE_LOCATION` | 获取粗略位置（网络定位） |
| `ACCESS_BACKGROUND_LOCATION` | 在后台持续获取位置（核心功能） |
| `FOREGROUND_SERVICE` | 运行前台服务保持定位活跃 |
| `FOREGROUND_SERVICE_LOCATION` | 声明前台服务类型为位置 |
| `POST_NOTIFICATIONS` | 发送闹钟触发通知 (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | 开机自启恢复服务 |
| `INTERNET` | Geocoder 地址解析需要网络 |
| `VIBRATE` | 闹钟触发时振动提醒 |

## 使用指南

### 创建位置闹钟
1. 打开 APP，点击右下角 **+** 按钮
2. 输入**闹钟名称**（如"到公司提醒打卡"）
3. 输入**提醒内容**（如"记得打卡！"）
4. 在地址栏输入目标地址（如"百度大厦"），点击**搜索**
5. 确认搜索结果显示的位置正确
6. 设置**触发范围**（米），如 500 表示到达 500 米范围内时触发
7. 可选：添加**分类标签**
8. 点击**保存**

### 管理闹钟
- **启用/禁用**：点击闹钟卡片右侧的开关
- **编辑**：点击闹钟卡片上的编辑图标
- **删除**：点击闹钟卡片上的删除图标

### 查看历史
- 点击右上角**历史记录**按钮查看所有触发记录
- 可单条删除或一键清除全部

## 厂商适配说明

由于 Android 各厂商对后台服务的限制不同，首次安装后需要额外设置：

| 厂商 | 需要的设置 |
|------|-----------|
| **小米/红米** | 设置 → 应用管理 → 位置闹钟 → 自启动 → 允许 |
| **华为/荣耀** | 设置 → 电池 → 应用启动管理 → 位置闹钟 → 允许后台活动 |
| **OPPO/vivo** | 设置 → 电池 → 后台冻结/电池优化 → 将位置闹钟加入白名单 |
| **三星** | 设置 → 电池 → 自适应电池 → 关闭对位置闹钟的限制 |
| **原生 Pixel** | 通常无需额外设置 |

## 技术架构

### 定位策略
- 使用 `FusedLocationProviderClient` 获取位置（比原生 GPS 更省电）
- 定位间隔：30 秒（平衡电量与响应速度）
- 优先级：`PRIORITY_BALANCED_POWER_ACCURACY`
- 使用前台服务（Foreground Service）确保 APP 退出后仍可运行

### 触发逻辑
1. 每 30 秒获取一次当前位置
2. 遍历所有启用的闹钟，计算当前位置与目标位置的距离
3. 距离 ≤ 设定半径 → 触发通知 + 记录历史
4. 离开范围后自动重置，允许再次触发

### 数据存储
- **Room 数据库**：`alarms` 表存储闹钟，`alarm_history` 表存储触发记录
- 数据库版本：1，使用 `fallbackToDestructiveMigration` 策略

## 后续可扩展方向

- 地图选点（集成 Google Maps SDK 或高德地图 SDK）
- 多触发条件（进入/离开/停留 X 分钟）
- 重复规则（工作日/周末/自定义日期）
- 云端同步
- 桌面小组件
- 闹钟铃声自定义

## 许可证

本项目仅供学习和个人使用。
