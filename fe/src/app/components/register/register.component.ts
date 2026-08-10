import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  username = '';
  fullName = '';
  password = '';
  confirmPassword = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  onSubmit(): void {
    if (!this.username.trim() || !this.fullName.trim() || !this.password.trim()) return;

    if (this.password !== this.confirmPassword) {
      this.error.set('Mật khẩu xác nhận không khớp');
      return;
    }

    if (this.password.length < 3) {
      this.error.set('Mật khẩu phải có ít nhất 3 ký tự');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.auth.register({ username: this.username, password: this.password, fullName: this.fullName }).subscribe({
      next: () => {
        // Auto-login after registration
        this.auth.login({ username: this.username, password: this.password }).subscribe({
          next: () => this.router.navigate(['/']),
          error: () => this.router.navigate(['/login']),
        });
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Đăng ký thất bại. Tên đăng nhập có thể đã tồn tại.');
      },
    });
  }
}
