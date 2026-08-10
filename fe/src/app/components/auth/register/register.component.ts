import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { RegisterRequest } from '../../../core/models/auth.models';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
})
export class RegisterComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  confirmPassword = '';
  showPassword = false;

  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  get passwordMismatch(): boolean {
    return this.confirmPassword.length > 0 && this.password !== this.confirmPassword;
  }

  get isFormValid(): boolean {
    return (
      this.username.trim().length >= 3 &&
      this.password.length >= 8 &&
      this.password === this.confirmPassword
    );
  }

  onRegister(): void {
    if (!this.isFormValid) return;

    const request: RegisterRequest = {
      username: this.username.trim(),
      password: this.password,
    };

    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.authService
      .register(request)
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: () => {
          // Sau đăng ký thành công → tự động đăng nhập
          this.successMessage.set('Đăng ký thành công! Đang đăng nhập...');
          this.authService
            .login({ username: request.username, password: request.password })
            .subscribe({
              next: () => this.router.navigate(['/lobby']),
              error: () => this.router.navigate(['/login']),
            });
        },
        error: (err) => {
          const code = err?.error?.errorCode;
          if (code === 'USERNAME_ALREADY_EXISTS') {
            this.errorMessage.set(`Tên đăng nhập "${request.username}" đã được sử dụng. Vui lòng chọn tên khác.`);
          } else if (code === 'VALIDATION_ERROR') {
            this.errorMessage.set('Dữ liệu không hợp lệ. Kiểm tra lại tên đăng nhập và mật khẩu.');
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
