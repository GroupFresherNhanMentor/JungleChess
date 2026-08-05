#!/usr/bin/env bash

# 1. Đường dẫn tới thư mục migration của bạn (Hãy sửa lại cho đúng với cấu trúc dự án)
MIGRATION_DIR="src/main/resources/db/migration"

# 2. Kiểm tra xem thư mục có tồn tại không, nếu không thì tự động tạo mới
if [ ! -dir "$MIGRATION_DIR" ]; then
  mkdir -p "$MIGRATION_DIR"
fi

# 3. Lấy tên mô tả từ tham số truyền vào (ví dụ: ./create_migration.sh init_db)
DESC=$1

# Kiểm tra nếu người dùng quên nhập tên mô tả
if [ -z "$DESC" ]; then
  echo "❌ Lỗi: Vui lòng nhập tên mô tả cho file migration!"
  echo "👉 Ví dụ: $0 create_users_table"
  exit 1
fi

# 4. Tạo timestamp định dạng YYYYMMDDhhmmss (Ví dụ: 20260717161419)
TIMESTAMP=$(date "+%Y%m%d%H%M%S")

# 5. Định nghĩa tên file chuẩn Flyway (có 2 dấu gạch dưới __)
FILE_NAME="V${TIMESTAMP}__${DESC}.sql"
FULL_PATH="${MIGRATION_DIR}/${FILE_NAME}"

# 6. Tạo file rỗng
touch "$FULL_PATH"

echo "✅ Đã tạo file migration thành công!"
echo "📂 Đường dẫn: $FULL_PATH"
