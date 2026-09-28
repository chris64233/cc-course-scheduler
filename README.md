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

## 教室临时停用与成组修复排课

在多课程原子调课之上增加「教室停用 → 冻结修复任务 → 成组修复方案 → 原子确认」的闭环。

### 业务流程与规则

1. **停用登记**：停用事件指定教室、停用时间段（`timeSlot`，格式同课程时间段）、原因和外部事件号 `eventNo`、处理人员 `operator`。生效时立即找出该教室与停用时段冲突的全部课程安排，连同课程版本快照冻结为**一份修复任务**（任务记录停用范围、受影响课程原始排课与冻结版本）。
   - 相同外部事件号、相同内容重复登记幂等（返回 200 与既有事件）；同号异内容返回 409。
   - 停用生效后，新增/修改课程、批量排课、普通调课均不得把课程排入任一教室的停用时段（重叠即拦截，返回 409，预检接口会在冲突明细中给出停用原因和外部事件号）。
2. **停用范围调整**：按外部事件号调整停用时间段（教室、原因可选）。调整后在原修复任务上重新冻结受影响课程并**递增任务版本**；范围没有实际变化时幂等返回。此前提交的待处理修复方案在预检/确认时会得到 `TASK_VERSION_CHANGED` 冲突，不能把课程移入新的停用范围。
3. **修复方案**：方案通过修复业务号 `bizKey` 幂等（同号同内容返回首次结果，同号异内容 409），必须**覆盖任务中全部仍受影响的课程**，每条调整可换教室、换时段，也可让多门课程互换资源（方案内腾出的旧资源视为可用）。
   - 预检查沿用教师/教室冲突规则，并叠加停用范围、方案内部冲突、课程版本、任务版本、覆盖完整性校验；缺少任何一门的可行安排都会在冲突明细中逐门给出具体原因，且不能确认部分方案。
4. **原子确认**：确认时在同一写锁临界区内重新检查课程版本（冻结/提交后被普通调课改过即失败）、教师时间、目标教室停用状态和任务版本，全部通过才在一次事务中应用所有变更；任一失败整份方案不生效。普通调课、停用范围调整与修复确认共用同一把域锁，全局串行，旧方案不会覆盖较新的课程安排。
5. **停用取消**：取消后停用时段立即恢复可用，但**不会自动回退**已经完成的修复，停用事件、修复方案和审计记录完整保留；未完成的修复任务随之取消。需要恢复原排课时，按普通调课流程创建一份新的调课方案即可。
6. **记录与查询**：停用登记/调整/取消、修复确认均写入审计日志（`OUTAGE_REGISTER`/`OUTAGE_UPDATE`/`OUTAGE_CANCEL`/`REPAIR_RESCHEDULE`），修复日志含调整前后教室与时段、处理人员（`operator`）和修复业务号（`referenceNo`）。

### 停用修复与普通调课的关系

- 两者共用同一套冲突规则和原子应用机制：修复方案本质是「由停用事件驱动、必须全覆盖受影响课程」的成组调课，复用方案内旧资源腾出、互换时段、逐冲突明细返回等能力。
- 两者并发安全：课程增删改、普通调课确认、停用登记/范围调整/取消、修复确认共用同一把域锁并配合课程版本号做乐观校验，同一课程上的并发操作最多一个生效。
- 普通调课既不能把课程移入停用时段，也可以在修复前主动把受影响课程移出停用范围（移出后修复任务对账时不再要求覆盖该课程）；停用取消后恢复原排课同样走普通调课，二者互为补充，不存在自动回退。

### 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/room-outages` | 登记停用事件（按 `eventNo` 幂等；新建 201，重复 200，同号异内容 409） |
| GET | `/api/room-outages` | 查询全部停用事件，可按 `eventNo` 过滤 |
| GET | `/api/room-outages/{id}` | 按 ID 查询停用事件 |
| GET | `/api/room-outages/by-event/{eventNo}` | 按外部事件号查询停用事件 |
| POST | `/api/room-outages/by-event/{eventNo}/adjust` | 调整停用范围（时间段必填），任务版本递增 |
| POST | `/api/room-outages/by-event/{eventNo}/cancel?operator=张三` | 取消停用（不回退已完成修复，取消幂等） |
| GET | `/api/room-outages/tasks?eventNo=EVT-1` | 查询修复任务（停用影响范围、受影响课程与冻结快照、状态/版本） |
| GET | `/api/room-outages/tasks/{taskId}` | 查询单份修复任务详情 |
| POST | `/api/room-outages/tasks/{taskId}/repair-plans` | 提交修复方案（按 `bizKey` 幂等；新建 201，重复 200） |
| GET | `/api/room-outages/repair-plans?eventNo=EVT-1` | 查询修复方案 |
| GET | `/api/room-outages/repair-plans/{planId}` | 查询修复方案详情（调整明细、冲突明细、处理结果） |
| POST | `/api/room-outages/repair-plans/{planId}/pre-check` | 修复预检查，返回逐门课程的冲突明细 |
| POST | `/api/room-outages/repair-plans/{planId}/confirm` | 原子确认；存在冲突时返回 409 及全部冲突明细，方案不生效 |
| POST | `/api/room-outages/repair-plans/{planId}/reject` | 拒绝修复方案 |
| GET | `/api/room-outages/courses/{scheduleId}/change-chain` | 查询一门课程的完整变更链（创建、普通调课、停用修复，含处理人员与业务号） |

登记示例：

```json
POST /api/room-outages
{
  "eventNo": "OUTAGE-20260928-001",
  "classroom": "A101",
  "timeSlot": "周三 09:00-12:30",
  "reason": "设备检修",
  "operator": "管理员"
}
```

修复方案示例（`items` 必须覆盖任务全部受影响课程）：

```json
POST /api/room-outages/tasks/1/repair-plans
{
  "bizKey": "REPAIR-20260928-001",
  "operator": "教务员",
  "items": [
    {"scheduleId": 1, "newClassroom": "B202", "newTimeSlot": "周三 10:00-12:00"},
    {"scheduleId": 2, "newClassroom": "C303", "newTimeSlot": "周四 08:00-10:00"}
  ]
}
```

冲突明细类型（`conflictType`）：`TEACHER`（教师时间冲突）、`CLASSROOM`（教室被占）、`ROOM_OUTAGE`（目标教室停用中）、`INTERNAL_TEACHER`/`INTERNAL_CLASSROOM`（方案内部冲突）、`SCHEDULE_CHANGED`/`REVISION_CHANGED`（课程已被较新安排覆盖）、`SCHEDULE_NOT_FOUND`（课程已删除）、`MISSING_COVERAGE`（未覆盖全部受影响课程）、`TASK_VERSION_CHANGED`（停用范围已调整）。
