import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Language } from '../models/game.models';

import enTranslations from '../../../../public/lang/en.json';
import vnTranslations from '../../../../public/lang/vn.json';

@Injectable({
  providedIn: 'root'
})
export class LocalizationService {
  private currentLangSubject = new BehaviorSubject<Language>('vn');
  public currentLang$ = this.currentLangSubject.asObservable();

  private translations: Record<Language, Record<string, string>> = {
    en: enTranslations,
    vn: vnTranslations
  };

  public get currentLanguage(): Language {
    return this.currentLangSubject.value;
  }

  public setLanguage(lang: Language): void {
    if (this.translations[lang]) {
      this.currentLangSubject.next(lang);
    }
  }

  public translate(key: string, params?: Record<string, string>): string {
    const lang = this.currentLanguage;
    let text = this.translations[lang]?.[key] || this.translations['en']?.[key] || key;

    if (params) {
      Object.keys(params).forEach((paramKey) => {
        text = text.replace(new RegExp(`{${paramKey}}`, 'g'), params[paramKey]);
      });
    }

    return text;
  }
}
