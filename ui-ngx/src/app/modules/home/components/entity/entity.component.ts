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

import { BaseData, HasId } from '@shared/models/base-data';
import { FormBuilder, FormGroup, NgForm } from '@angular/forms';
import { PageComponent } from '@shared/components/page.component';
import { EventEmitter, Input, OnInit, Output, ViewChild, Directive } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { PageLink } from '@shared/models/page/page-link';

@Directive()
export abstract class EntityComponent<T extends BaseData<HasId>,
  P extends PageLink = PageLink,
  L extends BaseData<HasId> = T,
  C extends EntityTableConfig<T, P, L> = EntityTableConfig<T, P, L>>
  extends PageComponent implements OnInit {

  entityForm: FormGroup;

  @ViewChild('entityNgForm', {static: true}) entityNgForm: NgForm;

  isEditValue: boolean;

  @Input()
  set isEdit(isEdit: boolean) {
    this.isEditValue = isEdit;
    this.updateFormState();
  }

  get isEdit() {
    return this.isEditValue;
  }

  get isAdd(): boolean {
    return this.entityValue && !this.entityValue.id;
  }

  @Input()
  set entity(entity: T) {
    this.entityValue = entity;
    if (this.entityForm) {
      this.entityForm.reset(undefined, {emitEvent: false});
      this.entityForm.markAsPristine();
      this.updateForm(entity);
    }
  }

  get entity(): T {
    return this.entityValue;
  }

  @Output()
  entityAction = new EventEmitter<EntityAction<T>>();

  protected constructor(protected store: Store<AppState>,
                        protected fb: FormBuilder,
                        protected entityValue: T,
                        protected entitiesTableConfig: C) {
    super(store);
    this.entityForm = this.buildForm(this.entityValue);
  }

  ngOnInit() {
  }

  onEntityAction($event: Event, action: string) {
    const entityAction = {event: $event, action, entity: this.entity} as EntityAction<T>;
    let handled = false;
    if (this.entitiesTableConfig) {
      handled = this.entitiesTableConfig.onEntityAction(entityAction);
    }
    if (!handled) {
      this.entityAction.emit(entityAction);
    }
  }

  updateFormState() {
    if (this.entityForm) {
      if (this.isEditValue) {
        this.entityForm.enable({emitEvent: false});
      } else {
        this.entityForm.disable({emitEvent: false});
      }
    }
  }

  entityFormValue() {
    const formValue = this.entityForm ? {...this.entityForm.getRawValue()} : {};
    return this.prepareFormValue(formValue);
  }

  prepareFormValue(formValue: any): any {
    return formValue;
  }

  abstract buildForm(entity: T): FormGroup;

  abstract updateForm(entity: T);

}
