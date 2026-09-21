CREATE DATABASE IF NOT EXISTS aftersale_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS aftersale_stock DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS aftersale_core DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 模拟订单独立库（非独立订单服务，仅库表隔离；售后仍在 aftersale-service 内读）
CREATE DATABASE IF NOT EXISTS aftersale_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
