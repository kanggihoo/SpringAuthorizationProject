package org.example.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // User와의 연관관계 대신 식별자(userId)만 매핑
  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  @Column(name = "refresh_token", nullable = false, unique = true, length = 512)
  private String refreshToken;

  @Column(name = "expiry_date", nullable = false)
  private LocalDateTime expiryDate;

  @Builder
  public RefreshToken(Long userId, String refreshToken, LocalDateTime expiryDate) {
    this.userId = userId;
    this.refreshToken = refreshToken;
    this.expiryDate = expiryDate;
  }

  public void updateToken(String refreshToken, LocalDateTime expiryDate) {
    this.refreshToken = refreshToken;
    this.expiryDate = expiryDate;
  }
}
