# v0.3.0 内置训练方案与来源 / Built-in plans and sources

本轮内置 70 个 OpenMOBI 原创、可复制编辑的方案：椭圆机 21、单车 21、划船机 14、跑步机 14。名称沿用已有热身、恢复、稳定、耐力、间歇、金字塔、递进、冷却等类别。不同器材使用不同目标，不能互相启动。既有方案 ID、收藏兼容；已保存记录按原始方案快照解释。

v0.3.1 将同类方案集中展示，组内按强度或时长递进，HIIT 按入门 20 分钟、递进 30 分钟、长间歇 40 分钟相邻排列。排序独立于训练编排，不改变各器材方案数量、ID、收藏、阶段或历史快照。

## 参考材料与证据边界

核验日期：2026-10-04。

- 本地官方 App：中文版 4.5.16.1、国际版 2.1.14。检查编辑器、执行器、报告字段，以及 `CreateWorkoutRequest`、`WorkoutViewModel` 与方案编辑入口。可确认器材类型、阶段条件和目标字段，未取得可核验的完整离线官方课程库。不将字段定义当成训练处方，也不将本次编排称作官方课程。协议对照方法见 [验证说明](PROTOCOL_VERIFICATION.md)。
- [Concept2：Getting Started? Try these Workouts](https://www.concept2.com/blog/getting-started-try-these-workouts)：用于划频递进、短工作段与轻松恢复段交替，以及骑行踏频变化的结构参考。没有把 BikeErg 风门档位换算为其他品牌的阻力百分比。
- [American Heart Association：Warm Up, Cool Down](https://www.heart.org/en/healthy-living/exercise-and-physical-activity/fitness-basics/warm-up-cool-down)：用于完整训练前后渐进热身、冷却的编排依据；20 分钟及以上常规方案保留至少 5 分钟首尾阶段。
- [ACE：The 5-2-4 Walking Workout](https://www.acefitness.org/resources/pros/expert-articles/8928/the-5-2-4-walking-workout/)：用于交替较快步行、轻松恢复与个体化速度的结构参考。未照搬命名课程、固定速度或训练效果承诺。

上述来源提供原则与结构。本项目的目标数值、时长组合和名称映射由 OpenMOBI 自行编排；不能解释为上述机构对全部 70 个方案或特定硬件的认证。

## 器材差异

| 器材 | 数量 | 目标与调整 |
| --- | ---: | --- |
| 椭圆机 | 21 | 保留熟悉的阻力曲线；明确恢复、热身、冷却阶段。20 分钟间歇与金字塔改为各 5 分钟首尾，仍保持总时长 20 分钟。 |
| 单车 | 21 | 独立阻力范围比例 8/16/24/32/40%，工作段踏频约 65–100 rpm，按方案层次配置；恢复段关闭范围提醒。不是从椭圆机直接复制百分比。 |
| 划船机 | 14 | 以工作段 18–26 spm 的可编辑区间组织，重视动作节奏；不预置跨型号阻力或速度写入。包含热身至中等稳定等基础类别。 |
| 跑步机 | 14 | 手动速度参考 3–6 km/h，坡度默认 0%；热身、步行、恢复和渐进组织。开始记录不启动电机、不自动写入速度与坡度。 |

- 时长较短的“10 分钟间歇”是训练片段，详情明确提示另做充分热身与冷却；提供独立热身和冷却方案。
- 全部方案默认关闭声音、振动及心率区间；没有按年龄或身份猜测心率处方。单车与划船机工作段使用新的阶段频率范围，恢复／冷却关闭提醒。
- 阻力百分比描述设备可用档位范围，不代表最大心率、FTP、真实机械负荷或个人强度。详情支持复制修改。
- 所有写入继续受实际连接能力约束。公开训练结构不构成蓝牙兼容性证据；现有真机验证范围没有扩大。

## 数据与验证

实现入口：`core/src/main/kotlin/org/openmobifitness/core/BuiltinPrograms.kt`。

核心校验覆盖数量与唯一 ID、器材兼容性、分钟名称与真实总时长、恢复阶段关闭提示、方案 CSV 往返、跑步机无阻力字段、划船机无速度／坡度写入目标。CSV 导入按已有规则变为自定义方案，测试不要求保留内置标记。

## English

The library contains 70 original, editable OpenMOBI plans: 21 elliptical, 21 cycling, 14 rowing and 14 treadmill plans. Familiar warm-up, recovery, steady, endurance, interval, pyramid, progression and cooldown categories are retained. Existing IDs and favorites remain compatible; saved workouts use their original plan snapshots.

Version 0.3.1 groups related plans with increasing targets or durations, keeping the 20-, 30- and 40-minute HIIT variants adjacent. Presentation order is separate from workout authoring and does not change equipment-specific counts, IDs, favorites, stages or historical snapshots.

Sources were checked on 2026-10-04. Local inspection of the Chinese 4.5.16.1 and international 2.1.14 original apps established editor fields, stage conditions and execution semantics, but did not recover a verifiable complete offline course library. See the [protocol verification method](PROTOCOL_VERIFICATION.md). These plans are not presented as official courses.

- [Concept2](https://www.concept2.com/blog/getting-started-try-these-workouts) informs progressive stroke rates, alternating work/recovery and cycling cadence changes. BikeErg damper settings are not converted into another brand's resistance percentages.
- [American Heart Association](https://www.heart.org/en/healthy-living/exercise-and-physical-activity/fitness-basics/warm-up-cool-down) informs gradual warm-up/cooldown structure. Regular plans of 20 minutes or longer retain at least five minutes at each end.
- [ACE](https://www.acefitness.org/resources/pros/expert-articles/8928/the-5-2-4-walking-workout/) informs alternating faster walking and easy recovery with individual speed adjustments. Named courses, fixed speeds and outcome promises are not copied.

The sources provide principles and structure. OpenMOBI authors the actual target values, durations and category mapping; this does not imply endorsement of these plans or hardware.

Elliptical plans retain familiar resistance curves with clearer recovery stages. Cycling uses independently selected resistance-range proportions of 8/16/24/32/40%, with work cadence around 65–100 rpm. Rowing uses editable work ranges of 18–26 spm without cross-model resistance or speed writes. Treadmill plans provide manual 3–6 km/h guidance and default 0% incline; recording never starts the motor or writes speed/incline commands. Ten-minute interval fragments explicitly require separate warm-up and cooldown.

Sound, vibration and heart-rate ranges are off by default. Recovery/cooldown stages disable cadence alerts. Resistance percentages describe equipment levels, not heart rate, FTP, measured mechanical load or personal intensity. All writes remain constrained by verified device capabilities; plan availability does not expand hardware compatibility claims.

Tests cover counts, unique IDs, compatible targets, stated duration, recovery hints, CSV round trips and absence of incompatible control fields. Imported CSV plans become custom plans under the existing format rules.
