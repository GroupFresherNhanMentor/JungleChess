import {
  AfterViewChecked,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatMessage, PieceSide } from '../../core/models/game.models';
import { LocalizationService } from '../../core/services/localization.service';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.css'
})
export class ChatComponent implements AfterViewChecked {
  @Input() messages: ChatMessage[] = [];
  @Input() currentTurn: PieceSide = 0;
  @Output() sendMessage = new EventEmitter<string>();

  @ViewChild('chatContainer') private chatContainer!: ElementRef;

  newMessageText: string = '';

  quickEmotes: string[] = [
    'Chơi hay lắm! 👍',
    'Nước cờ độc đấy! 🔥',
    'Tấn công thôi! ⚔️',
    'Tới luôn bạn ơi! 😎',
    'Gà thế! 🐔'
  ];

  constructor(public loc: LocalizationService) {}

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  private scrollToBottom(): void {
    try {
      if (this.chatContainer) {
        this.chatContainer.nativeElement.scrollTop =
          this.chatContainer.nativeElement.scrollHeight;
      }
    } catch (err) {}
  }

  onSend(event?: Event): void {
    if (event) {
      event.preventDefault();
    }
    const trimmed = this.newMessageText.trim();
    if (trimmed) {
      this.sendMessage.emit(trimmed);
      this.newMessageText = '';
    }
  }

  sendQuickEmote(emoteText: string): void {
    this.sendMessage.emit(emoteText);
  }
}
