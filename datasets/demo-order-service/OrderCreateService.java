// 演示用 Java 代码片段。
// 这里故意保留一个 couponId 空值风险，供故障诊断 Agent 召回和分析。

public class OrderCreateService {

    private final CouponClient couponClient;

    public OrderCreateService(CouponClient couponClient) {
        this.couponClient = couponClient;
    }

    public void createOrder(CreateOrderRequest request) {
        // TODO: 生产代码应该先判断 couponId 是否为空。
        couponClient.validate(request.getCouponId());
        // 这里省略库存预占、订单落库和事件发送逻辑。
    }
}

