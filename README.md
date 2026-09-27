# cc-course-scheduler

课程排课服务。

保留现有业务代码与自动化测试，作为后续功能迭代的稳定起点。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 多门课程原子调课

一次调课以「调课方案」为单位，方案内可包含多条现有课程安排的调整，每条调整指定新的星期/时段（`newTimeSlot`，格式同课程时间段，如 `周三 10:00-12:00`）和新教室（`newClassroom`）。

### 主要业务规则

1. **方案组成**：一个方案至少包含一条调整，且引用的课程安排 ID 互不重复；提交时会保存每条课程的原始排课内容（课程名、老师、教室、时间段）作为快照。
2. **预检查**：检查冲突时把方案内课程即将腾出的旧时段/旧教室视为可用，因此允许两门或多门课程互换时段；但调整后的新时段/新教室不得与未参与调课的课程产生教师或教室冲突，方案内部的新安排之间也不得冲突。任一调整不合法都会返回整份方案的全部冲突明细（`canReschedule=false`）。
3. **原子确认**：确认时在写锁内重新校验——每条课程仍存在、且与提交时的快照一致（未被别人修改/删除）、目标资源未被占用、方案内部无冲突。全部通过才一次性应用所有变更；任一失败则整份方案不生效，不留部分调课结果。多个方案并发修改同一课程时最多一个成功（其余因快照不一致而失败，方案保持待处理）。
4. **幂等**：提交必须携带调课业务号 `bizKey`。相同业务号且方案内容相同重复提交时返回首次提交的结果（HTTP 200）；内容不同则返回 409 冲突。方案确认（CONFIRMED）或拒绝（REJECTED）后不可再次预检/确认/拒绝。
5. **审计**：确认成功后为每条课程写入一条 `RESCHEDULE` 审计日志，同时记录调整前（`previousClassroom`/`previousTimeSlot`）与调整后（`classroom`/`timeSlot`）的内容。

### 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/reschedule-plans` | 提交调课方案（按 `bizKey` 幂等；新建 201，重复提交 200） |
| GET | `/api/reschedule-plans` | 查询全部调课方案 |
| GET | `/api/reschedule-plans/{id}` | 查询方案详情（调整明细、冲突明细、处理结果） |
| POST | `/api/reschedule-plans/{id}/pre-check` | 预检查，返回整份方案的冲突明细 |
| POST | `/api/reschedule-plans/{id}/confirm` | 原子确认；存在冲突时返回 409 及冲突明细，方案不生效 |
| POST | `/api/reschedule-plans/{id}/reject` | 拒绝方案 |

提交示例：

```json
POST /api/reschedule-plans
{
  "bizKey": "RES-20260927-001",
  "items": [
    {"scheduleId": 1, "newClassroom": "B202", "newTimeSlot": "周二 14:00-16:00"},
    {"scheduleId": 2, "newClassroom": "A101", "newTimeSlot": "周一 08:00-10:00"}
  ]
}
```
