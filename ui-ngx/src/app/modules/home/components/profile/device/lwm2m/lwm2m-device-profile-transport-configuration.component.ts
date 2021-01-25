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

import { DeviceProfileTransportConfiguration, DeviceTransportType } from '@shared/models/device.models';
import { Component, forwardRef, Inject, Input } from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import {
  ATTR,
  getDefaultProfileConfig,
  Instance,
  KEY_NAME,
  ObjectLwM2M,
  OBSERVE,
  OBSERVE_ATTR,
  ProfileConfigModels,
  ResourceLwM2M,
  TELEMETRY
} from './profile-config.models';
import { DeviceProfileService } from '@core/http/device-profile.service';
import { deepClone, isDefinedAndNotNull, isUndefined } from '@core/utils';
import { WINDOW } from '@core/services/window.service';
import { JsonObject } from '@angular/compiler-cli/ngcc/src/packages/entry_point';
import { Direction } from '@shared/models/page/sort-order';

@Component({
  selector: 'tb-profile-lwm2m-device-transport-configuration',
  templateUrl: './lwm2m-device-profile-transport-configuration.component.html',
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => Lwm2mDeviceProfileTransportConfigurationComponent),
    multi: true
  }]
})
export class Lwm2mDeviceProfileTransportConfigurationComponent implements ControlValueAccessor, Validators {

  private configurationValue: ProfileConfigModels;
  private requiredValue: boolean;
  private disabled = false;

  lwm2mDeviceProfileFormGroup: FormGroup;
  lwm2mDeviceConfigFormGroup: FormGroup;
  observeAttr = OBSERVE_ATTR;
  observe = OBSERVE;
  attribute = ATTR;
  telemetry = TELEMETRY;
  keyName = KEY_NAME;
  bootstrapServers: string;
  bootstrapServer: string;
  lwm2mServer: string;
  sortFunction: (key: string, value: object) => object;

  get required(): boolean {
    return this.requiredValue;
  }

  @Input()
  set required(value: boolean) {
    this.requiredValue = coerceBooleanProperty(value);
  }

  private propagateChange = (v: any) => {
  };

  constructor(private store: Store<AppState>,
              private fb: FormBuilder,
              private deviceProfileService: DeviceProfileService,
              @Inject(WINDOW) private window: Window) {
    this.lwm2mDeviceProfileFormGroup = this.fb.group({
      objectIds: [{}, Validators.required],
      observeAttrTelemetry: [{clientLwM2M: []}, Validators.required],
      shortId: [null, Validators.required],
      lifetime: [null, Validators.required],
      defaultMinPeriod: [null, Validators.required],
      notifIfDisabled: [true, []],
      binding: ['U', Validators.required],
      bootstrapServer: [null, Validators.required],
      lwm2mServer: [null, Validators.required],
    });
    this.lwm2mDeviceConfigFormGroup = this.fb.group({
      configurationJson: [null, Validators.required]
    });
    this.lwm2mDeviceProfileFormGroup.valueChanges.subscribe(() => {
      console.warn('main form');
      if (!this.disabled) {
        this.updateModel();
      }
    });
    this.lwm2mDeviceConfigFormGroup.valueChanges.subscribe(() => {
      console.warn('config form');
      if (!this.disabled) {
        this.updateModel();
      }
    });
    this.sortFunction = this.sortObjectKeyPathJson;
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.lwm2mDeviceProfileFormGroup.disable({emitEvent: false});
      this.lwm2mDeviceConfigFormGroup.disable({emitEvent: false});
    } else {
      this.lwm2mDeviceProfileFormGroup.enable({emitEvent: false});
      this.lwm2mDeviceConfigFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: any | null): void {
    this.configurationValue = (Object.keys(value).length === 0) ? getDefaultProfileConfig() : value;
    this.lwm2mDeviceConfigFormGroup.patchValue({
      configurationJson: this.configurationValue
    }, {emitEvent: false});
    this.initWriteValue();
  }

  private initWriteValue = (): void => {
    const modelValue = {objectIds: null, objectsList: []};
    modelValue.objectIds = this.getObjectsFromJsonAllConfig();
    if (modelValue.objectIds !== null) {
      const sortOrder = {
        property: 'id',
        direction: Direction.ASC
      };
      this.deviceProfileService.getLwm2mObjects(sortOrder, modelValue.objectIds, null).subscribe(
        (objectsList) => {
          modelValue.objectsList = objectsList;
          this.updateWriteValue(modelValue);
        }
      );
    } else {
      this.updateWriteValue(modelValue);
    }
  }

  private updateWriteValue = (value: any): void => {
    const objectsList = value.objectsList;
    this.lwm2mDeviceProfileFormGroup.patchValue({
        objectIds: value,
        observeAttrTelemetry: {clientLwM2M: this.getObserveAttrTelemetryObjects(objectsList)},
        shortId: this.configurationValue.bootstrap.servers.shortId,
        lifetime: this.configurationValue.bootstrap.servers.lifetime,
        defaultMinPeriod: this.configurationValue.bootstrap.servers.defaultMinPeriod,
        notifIfDisabled: this.configurationValue.bootstrap.servers.notifIfDisabled,
        binding: this.configurationValue.bootstrap.servers.binding,
        bootstrapServer: this.configurationValue.bootstrap.bootstrapServer,
        lwm2mServer: this.configurationValue.bootstrap.lwm2mServer
      },
      {emitEvent: false});
  }

  private updateModel = (): void => {
    let configuration: DeviceProfileTransportConfiguration = null;
    if (this.lwm2mDeviceConfigFormGroup.valid) {
      this.upDateValueToJson();
      configuration = this.lwm2mDeviceConfigFormGroup.getRawValue().configurationJson;
      configuration.type = DeviceTransportType.LWM2M;
    }
    this.propagateChange(configuration);
  }

  private updateObserveAttrTelemetryObjectFormGroup = (objectsList: ObjectLwM2M[]): void => {
    this.lwm2mDeviceProfileFormGroup.patchValue({
        observeAttrTelemetry: {clientLwM2M: this.getObserveAttrTelemetryObjects(objectsList)}
      },
      {emitEvent: false});
    this.lwm2mDeviceProfileFormGroup.get('observeAttrTelemetry').markAsPristine({
      onlySelf: true
    });
  }

  private upDateValueToJson = (): void => {
    this.upDateValueToJsonTab0();
    this.upDateValueToJsonTab1();
  }

  private upDateValueToJsonTab0 = (): void => {
    if (!this.lwm2mDeviceProfileFormGroup.get('observeAttrTelemetry').pristine) {
      this.upDateObserveAttrTelemetryFromGroupToJson(
        this.lwm2mDeviceProfileFormGroup.get('observeAttrTelemetry').value.clientLwM2M
      );
      this.lwm2mDeviceProfileFormGroup.get('observeAttrTelemetry').markAsPristine({
        onlySelf: true
      });
      this.upDateJsonAllConfig();
    }
  }

  private upDateValueToJsonTab1 = (): void => {
    this.upDateValueServersToJson();
    if (!this.lwm2mDeviceProfileFormGroup.get('bootstrapServer').pristine) {
      this.configurationValue.bootstrap.bootstrapServer = this.lwm2mDeviceProfileFormGroup.get('bootstrapServer').value;
      this.lwm2mDeviceProfileFormGroup.get('bootstrapServer').markAsPristine({
        onlySelf: true
      });
      this.upDateJsonAllConfig();
    }
    if (!this.lwm2mDeviceProfileFormGroup.get('lwm2mServer').pristine) {
      this.configurationValue.bootstrap.lwm2mServer = this.lwm2mDeviceProfileFormGroup.get('lwm2mServer').value;
      this.lwm2mDeviceProfileFormGroup.get('lwm2mServer').markAsPristine({
        onlySelf: true
      });
      this.upDateJsonAllConfig();
    }
  }

  private upDateValueServersToJson = (): void => {
    const bootstrapServers = this.configurationValue.bootstrap.servers;
    if (!this.lwm2mDeviceProfileFormGroup.get('shortId').pristine) {
      bootstrapServers.shortId = this.lwm2mDeviceProfileFormGroup.get('shortId').value;
      this.lwm2mDeviceProfileFormGroup.get('shortId').markAsPristine({
        onlySelf: true
      });
      this.upDateJsonAllConfig();
    }
    if (!this.lwm2mDeviceProfileFormGroup.get('lifetime').pristine) {
      bootstrapServers.lifetime = this.lwm2mDeviceProfileFormGroup.get('lifetime').value;
      this.lwm2mDeviceProfileFormGroup.get('lifetime').markAsPristine({
        onlySelf: true
      });
      this.upDateJsonAllConfig();
    }
    if (!this.lwm2mDeviceProfileFormGroup.get('defaultMinPeriod').pristine) {
      bootstrapServers.defaultMinPeriod = this.lwm2mDeviceProfileFormGroup.get('defaultMinPeriod').value;
      this.lwm2mDeviceProfileFormGroup.get('defaultMinPeriod').markAsPristine({
        onlySelf: true
      });
      this.upDateJsonAllConfig();
    }
    if (!this.lwm2mDeviceProfileFormGroup.get('notifIfDisabled').pristine) {
      bootstrapServers.notifIfDisabled = this.lwm2mDeviceProfileFormGroup.get('notifIfDisabled').value;
      this.lwm2mDeviceProfileFormGroup.get('notifIfDisabled').markAsPristine({
        onlySelf: true
      });
      this.upDateJsonAllConfig();
    }
    if (!this.lwm2mDeviceProfileFormGroup.get('binding').pristine) {
      bootstrapServers.binding = this.lwm2mDeviceProfileFormGroup.get('binding').value;
      this.lwm2mDeviceProfileFormGroup.get('binding').markAsPristine({
        onlySelf: true
      });
      this.upDateJsonAllConfig();
    }
  }

  private getObserveAttrTelemetryObjects = (listObject: ObjectLwM2M[]): ObjectLwM2M [] => {
    const clientObserveAttrTelemetry = listObject;
    if (this.configurationValue[this.observeAttr]) {
      const observeArray = this.configurationValue[this.observeAttr][this.observe];
      const attributeArray = this.configurationValue[this.observeAttr][this.attribute];
      const telemetryArray = this.configurationValue[this.observeAttr][this.telemetry];
      const keyNameJson = this.configurationValue[this.observeAttr][this.keyName];
      if (this.includesNotZeroInstance(attributeArray, telemetryArray)) {
        this.addInstances(attributeArray, telemetryArray, clientObserveAttrTelemetry);
      }
      if (isDefinedAndNotNull(observeArray)) {
        this.updateObserveAttrTelemetryObjects(observeArray, clientObserveAttrTelemetry, 'observe');
      }
      if (isDefinedAndNotNull(attributeArray)) {
        this.updateObserveAttrTelemetryObjects(attributeArray, clientObserveAttrTelemetry, 'attribute');
      }
      if (isDefinedAndNotNull(telemetryArray)) {
        this.updateObserveAttrTelemetryObjects(telemetryArray, clientObserveAttrTelemetry, 'telemetry');
      }
      if (isDefinedAndNotNull(keyNameJson)) {
        this.updateKeyNameObjects(keyNameJson, clientObserveAttrTelemetry);
      }
    }
    clientObserveAttrTelemetry.forEach(obj => {
      obj.instances.sort((a, b) => a.id - b.id);
    });
    return clientObserveAttrTelemetry;
  }

  private includesNotZeroInstance = (attribute: string[], telemetry: string[]): boolean => {
    const isNotZeroInstanceId = (instance) => !instance.includes('/0/');
    return attribute.some(isNotZeroInstanceId) || telemetry.some(isNotZeroInstanceId);
  }

  private addInstances = (attribute: string[], telemetry: string[], clientObserveAttrTelemetry: ObjectLwM2M[]): void => {
    const instancesPath = attribute.concat(telemetry)
      .filter(instance => !instance.includes('/0/'))
      .map(instance => this.convertPathToInstance(instance))
      .sort();

    new Set(instancesPath).forEach(path => {
      const pathParameter = Array.from(path.split('/'), Number);
      const objectLwM2M = clientObserveAttrTelemetry.find(x => x.id === pathParameter[0]);
      if (objectLwM2M) {
        const instance = deepClone(objectLwM2M.instances[0]);
        instance.id = pathParameter[1];
        objectLwM2M.instances.push(instance);
      }
    });
  }

  private convertPathToInstance = (path: string): string => {
    const [objectId, instanceId] = path.substring(1).split('/');
    return `${objectId}/${instanceId}`;
  }

  private updateObserveAttrTelemetryObjects = (parameters: string[], clientObserveAttrTelemetry: ObjectLwM2M[],
                                               nameParameter: string): void => {
    parameters.forEach(parameter => {
      const [objectId, instanceId, resourceId] = Array.from(parameter.substring(1).split('/'), Number);
      clientObserveAttrTelemetry
        .forEach(key => {
          if (key.id === objectId) {
            const instance = key.instances.find(itrInstance => itrInstance.id === instanceId);
            if (isDefinedAndNotNull(instance)) {
              instance.resources.find(resource => resource.id === resourceId)[nameParameter] = true;
            }
          }
        });
    });
  }

  private updateKeyNameObjects = (nameJson: JsonObject, clientObserveAttrTelemetry: ObjectLwM2M[]): void => {
    const keyName = JSON.parse(JSON.stringify(nameJson));
    Object.keys(keyName).forEach(key => {
      const [objectId, instanceId, resourceId] = Array.from(key.substring(1).split('/'), Number);
      clientObserveAttrTelemetry
        .forEach(object => {
          if (object.id === objectId) {
            object.instances
              .find(instance => instance.id === instanceId).resources
              .find(resource => resource.id === resourceId).keyName = keyName[key];
          }
        });
    });
  }

  private upDateObserveAttrTelemetryFromGroupToJson = (val: ObjectLwM2M[]): void => {
    const observeArray: Array<string> = [];
    const attributeArray: Array<string> = [];
    const telemetryArray: Array<string> = [];
    const observeJson: ObjectLwM2M[] = JSON.parse(JSON.stringify(val));
    let pathObj;
    let pathInst;
    let pathRes;
    observeJson.forEach(obj => {
      Object.entries(obj).forEach(([key, value]) => {
        if (key === 'id') {
          pathObj = value;
        }
        if (key === 'instances') {
          const instancesJson = JSON.parse(JSON.stringify(value)) as Instance[];
          if (instancesJson.length > 0) {
            instancesJson.forEach(instance => {
              Object.entries(instance).forEach(([instanceKey, instanceValue]) => {
                if (instanceKey === 'id') {
                  pathInst = instanceValue;
                }
                if (instanceKey === 'resources') {
                  const resourcesJson = JSON.parse(JSON.stringify(instanceValue)) as ResourceLwM2M[];
                  if (resourcesJson.length > 0) {
                    resourcesJson.forEach(res => {
                      Object.entries(res).forEach(([resourceKey, resourceValue]) => {
                        if (resourceKey === 'id') {
                          // pathRes = resourceValue
                          pathRes = '/' + pathObj + '/' + pathInst + '/' + resourceValue;
                        } else if (resourceKey === 'observe' && resourceValue) {
                          observeArray.push(pathRes);
                        } else if (resourceKey === 'attribute' && resourceValue) {
                          attributeArray.push(pathRes);
                        } else if (resourceKey === 'telemetry' && resourceValue) {
                          telemetryArray.push(pathRes);
                        }
                      });
                    });
                  }
                }
              });
            });
          }
        }
      });
    });
    if (isUndefined(this.configurationValue[this.observeAttr])) {
      this.configurationValue[this.observeAttr] = {
        [this.observe]: observeArray,
        [this.attribute]: attributeArray,
        [this.telemetry]: telemetryArray
      };
    } else {
      this.configurationValue[this.observeAttr][this.observe] = observeArray;
      this.configurationValue[this.observeAttr][this.attribute] = attributeArray;
      this.configurationValue[this.observeAttr][this.telemetry] = telemetryArray;
    }
    this.updateKeyName();
  }

  sortObjectKeyPathJson = (key: string, value: object): object => {
    if (key === 'keyName') {
      return Object.keys(value).sort(this.sortPath).reduce((obj, keySort) => {
        obj[keySort] = value[keySort];
        return obj;
      }, {});
    } else if (key === 'observe' || key === 'attribute' || key === 'telemetry') {
      return Object.values(value).sort(this.sortPath).reduce((arr, arrValue) => {
        arr.push(arrValue);
        return arr;
      }, []);
    } else {
      return value;
    }
  }

  private sortPath = (a, b): number => {
    const aLC = Array.from(a.substring(1).split('/'), Number);
    const bLC = Array.from(b.substring(1).split('/'), Number);
    return aLC[0] === bLC[0] ? aLC[1] - bLC[1] : aLC[0] - bLC[0];
  }

  private updateKeyName = (): void => {
    const paths = new Set<string>();
    if (this.configurationValue[this.observeAttr][this.attribute]) {
      this.configurationValue[this.observeAttr][this.attribute].forEach(path => {
        paths.add(path);
      });
    }
    if (this.configurationValue[this.observeAttr][this.telemetry]) {
      this.configurationValue[this.observeAttr][this.telemetry].forEach(path => {
        paths.add(path);
      });
    }
    const keyNameNew = {};
    paths.forEach(path => {
      const pathParameter = this.findIndexesForIds(path);
      if (pathParameter.length === 3) {
        keyNameNew[path] = this.lwm2mDeviceProfileFormGroup.get('observeAttrTelemetry').value
          .clientLwM2M[pathParameter[0]].instances[pathParameter[1]].resources[pathParameter[2]][this.keyName];
      }
    });
    this.configurationValue[this.observeAttr][this.keyName] = this.sortObjectKeyPathJson('keyName', keyNameNew);
  }

  private findIndexesForIds = (path: string): number[] => {
    const pathParameter = Array.from(path.substring(1).split('/'), Number);
    const pathParameterIndexes: number[] = [];
    const objectsOld = deepClone(
      this.lwm2mDeviceProfileFormGroup.get('observeAttrTelemetry').value.clientLwM2M) as ObjectLwM2M[];
    let isIdIndex = (element) => element.id === pathParameter[0];
    const objIndex = objectsOld.findIndex(isIdIndex);
    if (objIndex >= 0) {
      pathParameterIndexes.push(objIndex);
      isIdIndex = (element) => element.id === pathParameter[1];
      const instIndex = objectsOld[objIndex].instances.findIndex(isIdIndex);
      if (instIndex >= 0) {
        pathParameterIndexes.push(instIndex);
        isIdIndex = (element) => element.id === pathParameter[2];
        const resIndex = objectsOld[objIndex].instances[instIndex].resources.findIndex(isIdIndex);
        if (resIndex >= 0) {
          pathParameterIndexes.push(resIndex);
        }
      }
    }
    return pathParameterIndexes;
  }

  private getObjectsFromJsonAllConfig = (): number [] => {
    const objectsIds = new Set<number>();
    if (this.configurationValue[this.observeAttr]) {
      if (this.configurationValue[this.observeAttr][this.observe]) {
        this.configurationValue[this.observeAttr][this.observe].forEach(obj => {
          objectsIds.add(Array.from(obj.substring(1).split('/'), Number)[0]);
        });
      }
      if (this.configurationValue[this.observeAttr][this.attribute]) {
        this.configurationValue[this.observeAttr][this.attribute].forEach(obj => {
          objectsIds.add(Array.from(obj.substring(1).split('/'), Number)[0]);
        });
      }
      if (this.configurationValue[this.observeAttr][this.telemetry]) {
        this.configurationValue[this.observeAttr][this.telemetry].forEach(obj => {
          objectsIds.add(Array.from(obj.substring(1).split('/'), Number)[0]);
        });
      }
    }
    return (objectsIds.size > 0) ? Array.from(objectsIds) : null;
  }

  private upDateJsonAllConfig = (): void => {
    this.lwm2mDeviceProfileFormGroup.patchValue({
      configurationJson: this.configurationValue
    }, {emitEvent: false});
    this.lwm2mDeviceProfileFormGroup.markAsPristine({
      onlySelf: true
    });
  }

  addObjectsList = (value: ObjectLwM2M[]): void => {
    this.updateObserveAttrTelemetryObjectFormGroup(deepClone(value));
  }

  removeObjectsList = (value: ObjectLwM2M): void => {
    const objectsOld = deepClone(this.lwm2mDeviceProfileFormGroup.get('observeAttrTelemetry').value.clientLwM2M);
    const isIdIndex = (element) => element.id === value.id;
    const index = objectsOld.findIndex(isIdIndex);
    if (index >= 0) {
      objectsOld.splice(index, 1);
    }
    this.updateObserveAttrTelemetryObjectFormGroup(objectsOld);
    this.removeObserveAttrTelemetryFromJson(this.observe, value.id);
    this.removeObserveAttrTelemetryFromJson(this.telemetry, value.id);
    this.removeObserveAttrTelemetryFromJson(this.attribute, value.id);
    this.removeObserveAttrTelemetryFromJson(this.attribute, value.id);
    this.removeKeyNameFromJson(value.id);
    this.upDateJsonAllConfig();
  }

  private removeObserveAttrTelemetryFromJson = (observeAttrTel: string, id: number): void => {
    const isIdIndex = (element) => Array.from(element.substring(1).split('/'), Number)[0] === id;
    let index = this.configurationValue[this.observeAttr][observeAttrTel].findIndex(isIdIndex);
    while (index >= 0) {
      this.configurationValue[this.observeAttr][observeAttrTel].splice(index, 1);
      index = this.configurationValue[this.observeAttr][observeAttrTel].findIndex(isIdIndex);
    }
  }

  private removeKeyNameFromJson = (id: number): void => {
    const keyNameJson = this.configurationValue[this.observeAttr][this.keyName];
    Object.keys(keyNameJson).forEach(key => {
      const idKey = Array.from(key.substring(1).split('/'), Number)[0];
      if (idKey === id) {
        delete keyNameJson[key];
      }
    });
  }

  isPathInJson(path: string): boolean {
    let isPath = this.findPathInJson(path, this.attribute);
    if (!isPath) {
      isPath = this.findPathInJson(path, this.telemetry);
    }
    return !!isPath;
  }

  private findPathInJson = (path: string, side: string): string => {
    if (this.configurationValue[this.observeAttr]) {
      if (this.configurationValue[this.observeAttr][side]) {
        return this.configurationValue[this.observeAttr][side].find(
          pathJs => pathJs === path);
      }
    }
  }
}
