///
/// Copyright © 2016-2019 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Injectable, NgZone } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  CanActivateChild,
  RouterStateSnapshot
} from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { select, Store } from '@ngrx/store';
import { AppState } from '../core.state';
import { selectAuth } from '../auth/auth.selectors';
import { catchError, map, skipWhile, take } from 'rxjs/operators';
import { AuthState } from '../auth/auth.models';
import { Observable, of } from 'rxjs';
import { enterZone } from '@core/operator/enterZone';
import { Authority } from '@shared/models/authority.enum';
import { DialogService } from '@core/services/dialog.service';
import { TranslateService } from '@ngx-translate/core';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate, CanActivateChild {

  constructor(private store: Store<AppState>,
              private authService: AuthService,
              private dialogService: DialogService,
              private translate: TranslateService,
              private zone: NgZone) {}

  getAuthState(): Observable<AuthState> {
    return this.store.pipe(
      select(selectAuth),
      skipWhile((authState) => !authState || !authState.isUserLoaded),
      take(1),
      enterZone(this.zone)
    );
  }

  canActivate(next: ActivatedRouteSnapshot,
              state: RouterStateSnapshot) {

    return this.getAuthState().pipe(
      map((authState) => {
        const url: string = state.url;

        let lastChild = state.root;
        while (lastChild.children.length) {
          lastChild = lastChild.children[0];
        }
        const data = lastChild.data || {};
        const isPublic = data.module === 'public';

        if (!authState.isAuthenticated) {
          if (!isPublic) {
            this.authService.redirectUrl = url;
            // this.authService.gotoDefaultPlace(false);
            return this.authService.defaultUrl(false);
          } else {
            return true;
          }
        } else {
          if (url === '/login') {
            // this.authService.gotoDefaultPlace(true);
            return this.authService.defaultUrl(true);
          } else {
            const authority = Authority[authState.authUser.authority];
            if (data.auth && data.auth.indexOf(authority) === -1) {
              this.dialogService.forbidden();
              return false;
            } else {
              return true;
            }
          }
        }
      }),
      catchError((err => { console.error(err); return of(false); } ))
    );
  }

  canActivateChild(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot) {
    return this.canActivate(route, state);
  }
}
