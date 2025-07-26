package io.qimo.usdtzero.notify;

import io.qimo.usdtzero.config.BotProperties;
import io.qimo.usdtzero.constant.ChainType;
import io.qimo.usdtzero.constant.OrderStatus;
import io.qimo.usdtzero.model.DailyStatistics;
import io.qimo.usdtzero.model.Order;
import io.qimo.usdtzero.service.LightweightMetricsService;
import io.qimo.usdtzero.service.OrderService;
import io.qimo.usdtzero.util.AmountConvertUtils;
import io.qimo.usdtzero.util.DateTimeFormatUtils;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DBMaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.abilitybots.api.bot.AbilityBot;
import org.telegram.telegrambots.abilitybots.api.db.MapDBContext;
import org.telegram.telegrambots.abilitybots.api.objects.Ability;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

import java.math.BigDecimal;
import jakarta.annotation.PostConstruct;

import static org.telegram.telegrambots.abilitybots.api.objects.Locality.ALL;
import static org.telegram.telegrambots.abilitybots.api.objects.Privacy.ADMIN;
import static org.telegram.telegrambots.abilitybots.api.objects.Privacy.PUBLIC;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

@Slf4j
public class TelegramCommandBot extends AbilityBot {
    
    private final BotProperties botProperties;
    private final LightweightMetricsService metricsService;
    private final OrderService orderService;

    public TelegramCommandBot(TelegramClient telegramClient, 
                             BotProperties botProperties,
                             LightweightMetricsService metricsService,
                             OrderService orderService) {
        super(telegramClient, "USDTZeroBot", new MapDBContext(
                DBMaker.memoryDB()
                        .closeOnJvmShutdown()
                        .transactionEnable()
                        .make()
        ));
        this.botProperties = botProperties;
        this.metricsService = metricsService;
        this.orderService = orderService;
        
        // 设置Bot命令菜单
        setBotCommands();
    }

    /**
     * 设置Bot命令菜单
     */
    private void setBotCommands() {
        try {
            SetMyCommands setMyCommands = SetMyCommands.builder()
                    .command(BotCommand.builder()
                            .command("start")
                            .description("开始使用USDTZero机器人")
                            .build())
                    .command(BotCommand.builder()
                            .command("status")
                            .description("查看系统运行状态")
                            .build())
                    .command(BotCommand.builder()
                            .command("order")
                            .description("查询订单详情")
                            .build())
                    .command(BotCommand.builder()
                            .command("summary")
                            .description("查看今日统计")
                            .build())
                    .build();
            
            silent.execute(setMyCommands);
            log.info("Bot命令菜单设置成功");
        } catch (Exception e) {
            log.error("设置Bot命令菜单失败", e);
        }
    }

    @Override
    public long creatorId() {
        return botProperties.getAdminId();
    }

    /**
     * 开始命令
     */
    public Ability start() {
        return Ability
                .builder()
                .name("start")
                .info("开始使用USDTZero机器人")
                .locality(ALL)
                .privacy(PUBLIC)
                .action(ctx -> {
                    String message = "欢迎使用USDTZero机器人！\n\n" +
                            "可用命令：\n" +
                            "/start - 开始使用\n" +
                            "/status - 查看系统状态\n" +
                            "/order - 查询订单详情\n" +
                            "/summary - 查看今日统计";
                    silent.send(message, ctx.chatId());
                })
                .build();
    }

    /**
     * 系统状态命令
     */
    public Ability status() {
        return Ability
                .builder()
                .name("status")
                .info("查看系统运行状态")
                .locality(ALL)
                .privacy(ADMIN)
                .action(ctx -> {
                    try {
                        // 获取JVM信息
                        Runtime runtime = Runtime.getRuntime();
                        long totalMemory = runtime.totalMemory();
                        long freeMemory = runtime.freeMemory();
                        long usedMemory = totalMemory - freeMemory;
                        long maxMemory = runtime.maxMemory();
                        
                        // 获取线程信息
                        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
                        while (rootGroup.getParent() != null) {
                            rootGroup = rootGroup.getParent();
                        }
                        int threadCount = rootGroup.activeCount();
                        
                        // 获取系统运行时间（修复计算错误）
                        long uptime = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
                        long days = uptime / (24 * 60 * 60 * 1000);
                        long hours = (uptime % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
                        long minutes = (uptime % (60 * 60 * 1000)) / (60 * 1000);
                        
                        // 获取CPU负载信息
                        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
                        double cpuLoad = osBean.getSystemLoadAverage();
                        int cpuCores = osBean.getAvailableProcessors();
                        
                        String message = String.format(
                            "🖥️ 系统状态报告\n\n" +
                            "📊 JVM 内存状态：\n" +
                            "• 已用内存：%.2f MB\n" +
                            "• 空闲内存：%.2f MB\n" +
                            "• 总内存：%.2f MB\n" +
                            "• 最大内存：%.2f MB\n" +
                            "• 内存使用率：%.1f%%\n\n" +
                            "🧵 线程信息：\n" +
                            "• 活跃线程数：%d\n\n" +
                            "⚡ CPU 信息：\n" +
                            "• CPU 核心数：%d\n" +
                            "• 系统负载：%.2f\n\n" +
                            "⏰ 系统运行时间：\n" +
                            "• %d天 %d小时 %d分钟\n\n" +
                            "💡 提示：使用 /summary 查看详细业务统计",
                            
                            usedMemory / 1024.0 / 1024.0,
                            freeMemory / 1024.0 / 1024.0,
                            totalMemory / 1024.0 / 1024.0,
                            maxMemory / 1024.0 / 1024.0,
                            (usedMemory * 100.0) / maxMemory,
                            threadCount,
                            cpuCores,
                            cpuLoad,
                            days, hours, minutes
                        );
                        
                        silent.send(message, ctx.chatId());
                    } catch (Exception e) {
                        log.error("获取系统状态失败", e);
                        silent.send("❌ 获取系统状态失败，请稍后重试", ctx.chatId());
                    }
                })
                .build();
    }

    /**
     * 订单查询命令
     */
    public Ability order() {
        return Ability
                .builder()
                .name("order")
                .info("查询订单详情")
                .locality(ALL)
                .privacy(ADMIN)
                .action(ctx -> {
                    String[] args = ctx.arguments();
                    if (args == null || args.length == 0 || args[0].trim().isEmpty()) {
                        // 没有参数时，提示用户输入
                        silent.send("🔍 请输入商户订单号或交易哈希：\n\n" +
                                "📝 使用方式：\n" +
                                "/order <订单号或txhash>", ctx.chatId());
                        return;
                    }
                    
                    // 有参数时，直接查询
                    String query = args[0].trim();
                    processOrderQuery(query, ctx.chatId());
                })
                .build();
    }

    /**
     * 处理订单查询
     */
    private void processOrderQuery(String key, Long chatId) {
        try {
            Order order = orderService.getByTradeNo(key);
            if (order == null){
                order = orderService.getByTxHash(key);
            }
            if (order == null){
                silent.send("❌ 订单未找到，请检查订单号或txhash是否正确", chatId);
                return;
            }
            
            String message = String.format(
                "📋 订单查询结果\n\n" +
                "🔍 查询条件：%s\n" +
                "📝 订单状态：%s\n" +
                "💰 订单金额：%.2f CNY\n" +
                "💵 USDT金额：%.2f USDT\n" +
                "📊 汇率：%s CNY/USDT\n" +
                "⏰ 创建时间：%s\n" +
                "🔗 交易哈希：%s\n" +
                "📱 商户订单号：%s\n" +
                "🌐 链类型：%s\n" +
                "💳 收款地址：%s",
                key,
                OrderStatus.getChineseName(order.getStatus()),
                AmountConvertUtils.calculateCnyFromMinUnit(order.getAmount()),
                AmountConvertUtils.calculateUsdtFromMinUnit(order.getActualAmount(), ChainType.getUsdtUnit(order.getChainType()), order.getScale()),
                order.getRate(),
                DateTimeFormatUtils.formatDateTime(order.getCreateTime()),
                order.getTxHash() != null ? order.getTxHash() : "",
                order.getTradeNo(),
                order.getChainType(),
                order.getAddress()
            );
            
            silent.send(message, chatId);
        } catch (Exception e) {
            log.error("查询订单失败", e);
            silent.send("❌ 查询订单失败，请检查订单号或txhash是否正确", chatId);
        }
    }

    /**
     * 每日统计命令
     */
    public Ability dailyStats() {
        return Ability
                .builder()
                .name("summary")
                .info("查看详细每日统计")
                .locality(ALL)
                .privacy(ADMIN)
                .action(ctx -> {
                    try {
                        DailyStatistics dailyStats = metricsService.generateDailyStatistics();
                        
                        String message = String.format(
                            "📊 每日统计报告\n\n" +
                            "📅 日期：%s\n\n" +
                            "💰 订单统计：\n" +
                            "• 成功订单：%d\n" +
                            "• 总订单数：%d\n" +
                            "• 成功率：%.2f%%\n\n" +
                            "💵 收款汇总：\n" +
                            "• CNY：%.2f CNY\n" +
                            "• TRC20：%.2f USDT\n" +
                            "• SPL：%.2f USDT\n" +
                            "• BEP20：%.2f USDT\n" +
                            "• 总计：%.2f USDT\n\n" +
                            "🔍 扫块统计：\n" +
                            "• TRC20：成功 %d，失败 %d (成功率 %.2f%%)\n" +
                            "• SPL：成功 %d，失败 %d (成功率 %.2f%%)\n" +
                            "• BEP20：成功 %d，失败 %d (成功率 %.2f%%)",
                            
                            dailyStats.getDate(),
                            dailyStats.getSuccessOrderCount(),
                            dailyStats.getTotalOrderCount(),
                            dailyStats.getOrderSuccessRate(),
                            dailyStats.getTotalCnyAmount(),
                            dailyStats.getTotalTrc20Amount(),
                            dailyStats.getTotalSplAmount(),
                            dailyStats.getTotalBep20Amount(),
                            dailyStats.getTotalUsdtAmount(),
                            dailyStats.getTrc20SuccessCount(),
                            dailyStats.getTrc20FailCount(),
                            dailyStats.getTrc20SuccessRate(),
                            dailyStats.getSplSuccessCount(),
                            dailyStats.getSplFailCount(),
                            dailyStats.getSplSuccessRate(),
                            dailyStats.getBep20SuccessCount(),
                            dailyStats.getBep20FailCount(),
                            dailyStats.getBep20SuccessRate()
                        );
                        
                        silent.send(message, ctx.chatId());
                    } catch (Exception e) {
                        log.error("生成统计报告失败", e);
                        silent.send("❌ 生成统计报告失败，请稍后重试", ctx.chatId());
                    }
                })
                .build();
    }
}