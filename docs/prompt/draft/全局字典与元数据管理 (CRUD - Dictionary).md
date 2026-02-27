# Task 20: Unified Dictionary Management (CRUD)
实现系统字典表（t_dictionary）的通用管理功能：
1. **通用查询**：
   - 提供按 `dict_type` 获取全量键值对的接口，支持本地缓存（如 Caffeine 或 Redis）以提高性能。
2. **层级与分组**：
   - 支持 `group_key` 分组查询，方便前端按业务模块加载字典。
3. **系统保护**：
   - 逻辑校验：`is_system=1` 的字典项禁止通过 API 删除，仅允许修改 `dict_name` 或 `sort_order`。
4. **扩展性**：
   - `ext_json` 字段的读写支持，允许存储特定字典项的 UI 颜色、图标或特殊的业务控制参数。