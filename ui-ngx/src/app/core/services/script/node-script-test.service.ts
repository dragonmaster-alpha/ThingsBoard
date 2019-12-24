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

import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { RuleChainService } from '@core/http/rule-chain.service';
import { map, switchMap } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class NodeScriptTestService {

  constructor(private ruleChainService: RuleChainService) {
  }

  testNodeScript(script: string, scriptType: string, functionTitle: string,
                 functionName: string, argNames: string[], ruleNodeId: string): Observable<string> {
    if (ruleNodeId) {
      return this.ruleChainService.getLatestRuleNodeDebugInput(ruleNodeId).pipe(
        switchMap((debugIn) => {
          let msg: any;
          let metadata: any;
          let msgType: string;
          if (debugIn) {
            if (debugIn.data) {
              msg = JSON.parse(debugIn.data);
            }
            if (debugIn.metadata) {
              metadata = JSON.parse(debugIn.metadata);
            }
            msgType = debugIn.msgType;
          }
          return this.openTestScriptDialog(script, scriptType, functionTitle,
            functionName, argNames, msg, metadata, msgType);
        })
      );
    } else {
      return this.openTestScriptDialog(script, scriptType, functionTitle,
        functionName, argNames);
    }
  }

  private openTestScriptDialog(script: string, scriptType: string,
                               functionTitle: string, functionName: string, argNames: string[],
                               msg?: any, metadata?: any, msgType?: string): Observable<string> {
    if (!msg) {
      msg = {
        temperature: 22.4,
        humidity: 78
      };
    }
    if (!metadata) {
      metadata = {
        deviceType: 'default',
        deviceName: 'Test Device',
        ts: new Date().getTime() + ''
      };
    }
    if (!msgType) {
      msgType = 'POST_TELEMETRY_REQUEST';
    }
    console.log(`testNodeScript TODO: ${script}`);
    return of(script);
  }

}
