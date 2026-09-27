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

一次调课以“方案（Plan）”为单位：一个方案包含多条现有课程安排，每条安排指定新的星期/时段（`timeSlot`，如 `周三 10:00-12:00`）和教室（`classroom`）；调课不改变课程名与授课教师。

### 主要业务规则

1. **方案条目**：一次方案可包含多条安排；同一方案内引用的课程安排 ID 必须互不重复，条目列表不能为空。
2. **原始快照**：提交方案时冻结每条引用课程“当时看到的”原始排课内容（课程名、教师、教室、时段），确认时据此判断课程后续是否被别人修改或删除。
3. **预检查（整份方案视角）**：
   - 方案内课程即将腾出的旧时段一并纳入计算，因此两门或多门课程交换时段（含同教师互换）合法；
   - 与**未参与调课**的课程发生教师或教室冲突仍会被阻止；
   - 方案内两条安排的目标时段互相冲突（同教师或同教室）也会被阻止；
   - 任一项不合法都返回整份方案的全部冲突明细（`issues`），支持多次预检。
4. **确认的原子性**：确认时在同一把排课写锁内重新校验并一次性应用全部变更。只要任一课程已被修改、删除，或目标教师/教室资源被占用，整份方案都不生效，不会留下部分调课结果。多个方案并发修改同一课程时最多一个成功（失败者保持待处理状态并返回冲突明细，可在外部状态恢复后重试）。
5. **业务号幂等**：
   - 相同业务号 + 相同方案重复提交，返回首次创建的方案，不产生新方案；
   - 相同业务号 + 不同内容提交，返回 409 冲突；
   - 方案确认或拒绝后进入终态，不可再次处理（重复确认/拒绝返回首次结果，跨操作重复处理返回 409）。
6. **审计**：确认成功为每条课程记录调整前后的调课审计（操作类型 `RESCHEDULE`）；确认失败记录 `RESCHEDULE_FAILED`；拒绝记录 `RESCHEDULE_REJECT`。确认后的每条课程变更明细（before/after 教室与时段）长期保留在方案上可供查询。

### HTTP 接口（`/api/reschedules`）

路径参数 `{key}` 既支持系统生成的方案 ID（`planId`），也支持提交时的业务号（`businessId`）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/reschedules` | 提交调课方案（业务号幂等） |
| GET | `/api/reschedules?status=PENDING\|CONFIRMED\|REJECTED` | 查询方案列表，可按状态过滤 |
| GET | `/api/reschedules/{key}` | 查询方案详情（原始快照、目标排课、最近冲突、变更明细） |
| POST | `/api/reschedules/{key}/pre-check` | 预检查，返回整份方案冲突明细 |
| POST | `/api/reschedules/{key}/confirm` | 确认调课（全有或全无；失败时 HTTP 200 且 `applied=false`） |
| POST | `/api/reschedules/{key}/reject` | 拒绝方案（终态） |
| GET | `/api/reschedules/{key}/result` | 查询最近一次处理结果 |
| GET | `/api/reschedules/{key}/issues` | 查询方案冲突明细 |
| GET | `/api/reschedules/{key}/changes` | 查询确认后每条课程调整前后的变更明细 |

提交请求示例：

```json
{
  "businessId": "BIZ-20260927-001",
  "items": [
    { "scheduleId": 1, "classroom": "B202", "timeSlot": "周一 10:00-12:00" },
    { "scheduleId": 2, "classroom": "A101", "timeSlot": "周一 08:00-10:00" }
  ]
}
```

冲突明细 `issues` 中：

- `issueType`：`SCHEDULE_DELETED`（引用课程已删除）、`SCHEDULE_CHANGED`（原始快照过期）、`TEACHER`（教师冲突）、`CLASSROOM`（教室冲突）；
- `source`：`STALE_PLAN`（快照过期）、`EXTERNAL_COURSE`（与未参与调课课程冲突）、`INTERNAL_ITEM`（方案内条目互相冲突，此时 `otherItemIndex` 指向冲突条目）。
