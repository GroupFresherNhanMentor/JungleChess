import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { LoginRequest } from '../../../core/models/auth.models';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  showPassword = false;

  isLoading = signal(false);
  errorMessage = signal<string | null>(null);

  onLogin(): void {
    if (!this.username.trim() || !this.password) return;

    const request: LoginRequest = {
      username: this.username.trim(),
      password: this.password,
    };

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService
      .login(request)
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: () => this.router.navigate(['/lobby']),
        error: (err) => {
          const code = err?.error?.errorCode;
          if (code === 'INVALID_CREDENTIALS') {
            this.errorMessage.set('Sai tên đăng nhập hoặc mật khẩu.');
          } else if (code === 'ACCOUNT_LOCKED') {
            this.errorMessage.set('Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.');
          } else {
            this.errorMessage.set('Đã có lỗi xảy ra. Vui lòng thử lại.');
          }
        },
      });
  }

  toggleShowPassword(): void {
    this.showPassword = !this.showPassword;
  }
}
