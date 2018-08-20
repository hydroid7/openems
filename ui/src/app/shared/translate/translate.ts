import { TranslateLoader } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';

import { TRANSLATION as DE } from './de';
import { TRANSLATION as EN } from './en';
import { TRANSLATION as CZ } from './cz';
import { TRANSLATION as NL } from './nl';

export class MyTranslateLoader implements TranslateLoader {

    constructor() { }

    getTranslation(lang: string): Observable<any> {
        if (lang == 'de') {
            return of(DE);
        } else if (lang == 'cz') {
            return of(CZ);
        } else if (lang == 'nl') {
            return of(NL);
        } else {
            return of(EN);
        }
    }
}
