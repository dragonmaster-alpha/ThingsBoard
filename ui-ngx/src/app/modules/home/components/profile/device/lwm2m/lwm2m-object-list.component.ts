///
/// Copyright © 2016-2020 The Thingsboard Authors
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

import { Component, ElementRef, EventEmitter, forwardRef, Input, OnInit, Output, ViewChild } from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR, Validators } from '@angular/forms';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Observable } from 'rxjs';
import { filter, map, mergeMap, share, tap } from 'rxjs/operators';
import { ObjectLwM2M } from './profile-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DeviceProfileService } from '@core/http/device-profile.service';
import { PageLink } from '@shared/models/page/page-link';
import { Direction } from '@shared/models/page/sort-order';

@Component({
  selector: 'tb-profile-lwm2m-object-list',
  templateUrl: './lwm2m-object-list.component.html',
  styleUrls: [],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => Lwm2mObjectListComponent),
      multi: true
    }]
})
export class Lwm2mObjectListComponent implements ControlValueAccessor, OnInit, Validators {

  private requiredValue: boolean;
  private dirty = false;

  lwm2mListFormGroup: FormGroup;
  modelValue: Array<number> | null;
  objectsList: Array<ObjectLwM2M> = [];
  filteredObjectsList: Observable<Array<ObjectLwM2M>>;
  disabled = false;
  searchText = '';

  get required(): boolean {
    return this.requiredValue;
  }

  @Input()
  set required(value: boolean) {
    const newVal = coerceBooleanProperty(value);
    if (this.requiredValue !== newVal) {
      this.requiredValue = newVal;
      this.updateValidators();
    }
  }

  @Output()
  addList = new EventEmitter<any>();

  @Output()
  removeList = new EventEmitter<any>();

  @ViewChild('objectInput') objectInput: ElementRef<HTMLInputElement>;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              public translate: TranslateService,
              private deviceProfileService: DeviceProfileService,
              private fb: FormBuilder) {
    this.lwm2mListFormGroup = this.fb.group({
      objectsList: [this.objectsList],
      objectLwm2m: [null, this.required ? [Validators.required] : []]
    });
  }

  private updateValidators = (): void => {
    this.lwm2mListFormGroup.get('objectLwm2m').setValidators(this.required ? [Validators.required] : []);
    this.lwm2mListFormGroup.get('objectLwm2m').updateValueAndValidity();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.filteredObjectsList = this.lwm2mListFormGroup.get('objectLwm2m').valueChanges
      .pipe(
        tap((value) => {
          if (value && typeof value !== 'string') {
            this.add(value);
          } else if (value === null) {
            this.clear(this.objectInput.nativeElement.value);
          }
        }),
        filter((value) => typeof value === 'string'),
        mergeMap(name => this.fetchListObjects(name)),
        share()
      );
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.lwm2mListFormGroup.disable({emitEvent: false});
    } else {
      this.lwm2mListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: any): void {
    this.searchText = '';
    const objectIds = 'objectIds';
    if (value.hasOwnProperty(objectIds) && value[objectIds] != null && value[objectIds].length > 0) {
      this.modelValue = [...value[objectIds]];
      this.objectsList = value.objectsList;
    } else {
      this.objectsList = [];
      this.modelValue = null;
    }
    this.lwm2mListFormGroup.get('objectsList').setValue(this.objectsList);
    this.dirty = true;
  }

  private add(object: ObjectLwM2M): void {
    if (!this.modelValue || this.modelValue.indexOf(object.id) === -1) {
      if (!this.modelValue) {
        this.modelValue = [];
      }
      this.modelValue.push(object.id);
      this.objectsList.push(object);
      this.lwm2mListFormGroup.get('objectsList').setValue(this.objectsList);
      this.addList.next(this.objectsList);
    }
    this.propagateChange(this.modelValue);
    this.clear();
  }

  remove = (object: ObjectLwM2M): void => {
    let index = this.objectsList.indexOf(object);
    if (index >= 0) {
      this.objectsList.splice(index, 1);
      this.lwm2mListFormGroup.get('objectsList').setValue(this.objectsList);
      index = this.modelValue.indexOf(object.id);
      this.modelValue.splice(index, 1);
      this.removeList.next(object);
      if (!this.modelValue.length) {
        this.modelValue = null;
      }
      this.propagateChange(this.modelValue);
      this.clear();
    }
  }

  displayObjectLwm2mFn = (object?: ObjectLwM2M): string | undefined => {
    return object ? object.name : undefined;
  }

  // @TODO: add support page size
  // @TODO: filter for id + name
  private fetchListObjects = (searchText?: string): Observable<Array<ObjectLwM2M>> => {
    this.searchText = searchText;
    const pageLink = new PageLink(50, 0, searchText, {
      property: 'name',
      direction: Direction.ASC
    });
    return this.deviceProfileService.getLwm2mObjectsPage(pageLink, {ignoreLoading: true}).pipe(
      map(pageData => pageData.data)
    );
  }

  onFocus = (): void => {
    if (this.dirty) {
      this.lwm2mListFormGroup.get('objectLwm2m').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  private clear = (value: string = ''): void => {
    this.objectInput.nativeElement.value = value;
    this.lwm2mListFormGroup.get('objectLwm2m').patchValue(value, {emitEvent: true});
    setTimeout(() => {
      this.objectInput.nativeElement.blur();
      this.objectInput.nativeElement.focus();
    }, 0);
  }
}
