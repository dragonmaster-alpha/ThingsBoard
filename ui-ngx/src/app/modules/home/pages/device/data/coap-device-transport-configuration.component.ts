///
/// Copyright © 2016-2021 The Thingsboard Authors
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

import { Component, forwardRef, Input, OnDestroy, OnInit } from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import {
  CoapDeviceTransportConfiguration,
  DeviceTransportConfiguration,
  DeviceTransportType
} from '@shared/models/device.models';
import { PowerMode, PowerModeTranslationMap } from '@home/components/profile/device/lwm2m/lwm2m-profile-config.models';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull } from '@core/utils';

@Component({
  selector: 'tb-coap-device-transport-configuration',
  templateUrl: './coap-device-transport-configuration.component.html',
  styleUrls: [],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => CoapDeviceTransportConfigurationComponent),
    multi: true
  }]
})
export class CoapDeviceTransportConfigurationComponent implements ControlValueAccessor, OnInit, OnDestroy {

  coapDeviceTransportForm: FormGroup;

  powerMods = Object.values(PowerMode);
  powerModeTranslationMap = PowerModeTranslationMap;

  private requiredValue: boolean;
  get required(): boolean {
    return this.requiredValue;
  }
  @Input()
  set required(value: boolean) {
    this.requiredValue = coerceBooleanProperty(value);
  }

  @Input()
  disabled: boolean;

  private destroy$ = new Subject();
  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              private fb: FormBuilder) {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.coapDeviceTransportForm = this.fb.group({
      powerMode: [null],
      edrxCycle: [{disabled: true, value: 0}, [Validators.required, Validators.min(0), Validators.pattern('[0-9]*')]],
      psmActivityTimer: [{disabled: true, value: 0}, [Validators.required, Validators.min(0), Validators.pattern('[0-9]*')]],
      pagingTransmissionWindow: [{disabled: true, value: 0}, [Validators.required, Validators.min(0), Validators.pattern('[0-9]*')]]
    });
    this.coapDeviceTransportForm.get('powerMode').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((powerMode: PowerMode) => {
      if (powerMode === PowerMode.E_DRX) {
        this.coapDeviceTransportForm.get('edrxCycle').enable({emitEvent: false});
        this.coapDeviceTransportForm.get('pagingTransmissionWindow').enable({emitEvent: false});
        this.disablePSKMode();
      } else if (powerMode === PowerMode.PSM) {
        this.coapDeviceTransportForm.get('psmActivityTimer').enable({emitEvent: false});
        this.disableEdrxMode();
      } else {
        this.disableEdrxMode();
        this.disablePSKMode();
      }
    });
    this.coapDeviceTransportForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.updateModel();
    });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.coapDeviceTransportForm.disable({emitEvent: false});
    } else {
      this.coapDeviceTransportForm.enable({emitEvent: false});
      this.coapDeviceTransportForm.get('powerMode').updateValueAndValidity({onlySelf: true});
    }
  }

  writeValue(value: CoapDeviceTransportConfiguration | null): void {
    if (isDefinedAndNotNull(value)) {
      this.coapDeviceTransportForm.patchValue(value, {emitEvent: false});
    } else {
      this.coapDeviceTransportForm.get('powerMode').patchValue(null, {emitEvent: false});
    }
    if (!this.disabled) {
      this.coapDeviceTransportForm.get('powerMode').updateValueAndValidity({onlySelf: true});
    }
  }

  private updateModel() {
    let configuration: DeviceTransportConfiguration = null;
    if (this.coapDeviceTransportForm.valid) {
      configuration = this.coapDeviceTransportForm.value;
      configuration.type = DeviceTransportType.COAP;
    }
    this.propagateChange(configuration);
  }

  private disablePSKMode() {
    this.coapDeviceTransportForm.get('psmActivityTimer').disable({emitEvent: false});
    this.coapDeviceTransportForm.get('psmActivityTimer').reset(0, {emitEvent: false});
  }

  private disableEdrxMode() {
    this.coapDeviceTransportForm.get('edrxCycle').disable({emitEvent: false});
    this.coapDeviceTransportForm.get('edrxCycle').reset(0, {emitEvent: false});
    this.coapDeviceTransportForm.get('pagingTransmissionWindow').disable({emitEvent: false});
    this.coapDeviceTransportForm.get('pagingTransmissionWindow').reset(0, {emitEvent: false});
  }
}
