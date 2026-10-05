# ConsignDesk API

知华科技（上海如静知华信息科技有限公司）· https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。未经书面授权不得商用。

同源`/api`，JSON请求，HttpOnly会话Cookie。GET `/auth/csrf`返回header和token；所有写请求携带该CSRF头，不关闭CSRF。登录POST `/auth/login`，本人GET `/auth/me`、POST `/auth/logout`及`/auth/password`。改密撤销会话；密码不出现在响应。错误为HTTP400/401/403/404/409/413/429及`{"code":"..."}`，调用方保留输入、刷新核对版本，不自动重放写入。

| 路径                                                  | 动作与输入                                                                                                                         |
| ----------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| GET /meta、/dashboard                                 | 表单目录及统计；货主不能读员工统计                                                                                                 |
| GET/POST /consignors；PUT/DELETE /consignors/{id}     | code,name,departmentId,contactNote,enabled,revision；删除受引用保护                                                                |
| GET/POST /items；GET/PUT /items/{id}                  | consignorId,name,category,conditionNote,askingPrice,minimumPrice,ownerPercent,holdDays,expiresOn,revision；详情含item/events/sales |
| DELETE /items/{id}?revision=N                         | 仅本人从未提交草稿                                                                                                                 |
| POST /items/{id}/{action}                             | submit,withdraw,approve,reject,receive,renew,collect,cancel；revision,note,externalReference,expiresOn，字段按命令校验             |
| GET/POST /sales                                       | itemReference,revision,price,externalReference；返回服务端分成快照                                                                 |
| POST /sales/import                                    | 上述销售输入数组，1–300行；原子事务                                                                                                |
| POST /sales/{id}/refund                               | externalReference,note；原销售全额冲减，不接收可修改金额                                                                           |
| GET /balances、/accounts/{consignorId}                | 有符号余额、到期/冻结/待追回及ledger/movements；货主仅本人                                                                         |
| POST /cash                                            | consignorId,kind(PAYOUT/RECOVERY/REVERSAL),amount,externalReference,note,reversalOf；冲正只按原单金额                              |
| GET /export/items、/export/sales、/export/ledger      | CSV；ledger另需finance权限                                                                                                         |
| GET/POST /admin/{type}；PUT/DELETE /admin/{type}/{id} | users,roles,departments,permissions,menus,dictionaries,settings；权限键、菜单键和参数键不可任意新建；基础部门不删除                |
| GET /audit                                            | 权限及部门范围过滤                                                                                                                 |

商品金额0.01–999999.99、最多两位小数；比例>0且<=100、最多两位；holdDays整数0–90；期限不能早于UTC今天或晚于730天。更新必须传原revision。已收货约定不可改写，收到实物后才能卖，未到期分成不能提前支付。货主主体由登录绑定决定，不信任请求中的其他主体ID。单类列表上限10000，超限返回413，不静默截断。
