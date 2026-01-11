package server.sql.user.uuid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UUIDGenerator {
	public static String generateAndInsertUUID(int userId, Connection conn) throws SQLException {
		String uuid = UUID.randomUUID().toString();
		String sql = "insert into user_uuid (user_id, uuid, issued_at) values (?, ?, CURRENT_TIMESTAMP)";
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setInt(1, userId);
			pstmt.setString(2, uuid);
			pstmt.executeUpdate();
		}
		return uuid;
	}
	
	public static String getUUIDByUserId(int userId, Connection conn) throws SQLException {
		String sql = "select uuid from user_uuid where user_id = ?";
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setInt(1, userId);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					return rs.getString("uuid");
				}
			}
		}
		return null;
	}
	
	public static boolean updateUUID(int userId, Connection conn) throws SQLException {
		String uuid = UUID.randomUUID().toString();
		String sql = "update user_uuid set uuid = ?, issued_at = CURRENT_TIMESTAMP where user_id = ?";
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, uuid);
			pstmt.setInt(2, userId);
			int rowsAffected = pstmt.executeUpdate();
			return rowsAffected > 0;
		}
	}
	
	/**
	 * 验证UUID是否有效
	 * @param uuid 要验证的UUID
	 * @param conn 数据库连接
	 * @return 如果UUID有效返回用户ID，否则返回null
	 * @throws SQLException 如果查询过程中发生数据库错误
	 */
	public static Integer validateUUID(String uuid, Connection conn) throws SQLException {
		String sql = "select user_id from user_uuid where uuid = ?";
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, uuid);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("user_id");
				}
			}
		}
		return null;
	}
}
