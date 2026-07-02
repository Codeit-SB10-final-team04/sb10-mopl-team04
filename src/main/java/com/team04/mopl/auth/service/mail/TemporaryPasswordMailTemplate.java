package com.team04.mopl.auth.service.mail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/**
 * 임시 비밀번호 이메일의 HTML 본문을 생성하는 클래스
 */
@Component
public class TemporaryPasswordMailTemplate {

	private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter EXPIRES_AT_FORMATTER =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	// 임시 비밀번호와 만료 시각을 포함한 HTML 이메일 본문을 생성
	public String create(String temporaryPassword, Instant expiresAt) {
		String formattedExpiresAt = EXPIRES_AT_FORMATTER.format(
			expiresAt.atZone(KOREA_ZONE_ID)
		);

		return """
			<!doctype html>
			<html lang="ko">
			<head>
				<meta charset="UTF-8">
				<meta name="viewport" content="width=device-width, initial-scale=1.0">
				<title>임시 비밀번호 발급</title>
			</head>
			<body style="margin:0; padding:0; background-color:#ffffff; font-family:Arial, 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif; color:#222222;">
				<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#ffffff;">
					<tr>
						<td align="center" style="padding:48px 20px;">
							<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:810px; width:100%%;">
								<tr>
									<td align="center" style="padding-bottom:28px;">
										<h1 style="margin:0; font-size:32px; line-height:1.3; font-weight:700; color:#222222;">모두의 플리</h1>
									</td>
								</tr>
								<tr>
									<td align="center" style="padding-bottom:42px;">
										<h2 style="margin:0; font-size:28px; line-height:1.4; font-weight:700; color:#222222;">임시 비밀번호가 발급되었습니다</h2>
									</td>
								</tr>
								<tr>
									<td style="font-size:16px; line-height:1.8; color:#333333; padding-bottom:24px;">
										<p style="margin:0 0 18px 0;">안녕하세요!</p>
										<p style="margin:0;">
											요청하신 임시 비밀번호가 발급되었습니다. 아래 임시 비밀번호를 사용하여 로그인 후 새로운 비밀번호로 변경해 주세요.
										</p>
									</td>
								</tr>
								<tr>
									<td style="padding:8px 0 28px 0;">
										<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f7f8fa; border-left:5px solid #1683ff; border-radius:6px;">
											<tr>
												<td align="center" style="padding:28px 20px 8px 20px; font-size:16px; color:#333333;">
													임시 비밀번호
												</td>
											</tr>
											<tr>
												<td align="center" style="padding:0 20px 32px 20px; font-size:30px; line-height:1.3; font-weight:700; letter-spacing:3px; color:#1683ff; font-family:Consolas, Monaco, monospace;">
													%s
												</td>
											</tr>
										</table>
									</td>
								</tr>
								<tr>
									<td style="padding-bottom:42px;">
										<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#fff3cd; border-left:5px solid #ffc107; border-radius:6px;">
											<tr>
												<td style="padding:20px 24px; font-size:16px; line-height:1.7; color:#222222;">
													<div style="font-weight:700; margin-bottom:4px;">⚠️ 중요 안내사항</div>
													<div>• 이 임시 비밀번호는 <strong>%s</strong>까지만 유효합니다</div>
													<div>• 보안을 위해 로그인 후 즉시 새로운 비밀번호로 변경해주세요</div>
													<div>• 임시 비밀번호는 다른 사람과 공유하지 마세요</div>
												</td>
											</tr>
										</table>
									</td>
								</tr>
								<tr>
									<td align="center" style="font-size:14px; line-height:1.7; color:#666666;">
										본 메일은 발신전용이므로 회신되지 않습니다.<br>
										문의사항이 있으시면 고객센터로 연락해주세요.
									</td>
								</tr>
							</table>
						</td>
					</tr>
				</table>
			</body>
			</html>
			""".formatted(temporaryPassword, formattedExpiresAt);
	}
}
