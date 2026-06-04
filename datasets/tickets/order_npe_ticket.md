# INC-2026-001 订单创建接口空指针

现象：订单创建接口返回 500，日志中出现 NullPointerException。

根因：OrderCreateService 在 couponId 为空时仍然调用 CouponClient.validate(couponId)，导致空指针。

解决方案：增加 couponId 空值判断；没有优惠券时跳过优惠券校验；补充无优惠券创建订单的单元测试。

复盘：可选参数必须在 Service 层做防御性校验，不能只依赖前端传参。

