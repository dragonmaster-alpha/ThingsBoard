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

import {
  AfterViewInit,
  Component, ComponentFactoryResolver,
  ElementRef,
  Input,
  OnInit,
  Type,
  ViewChild
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageLink, TimePageLink } from '@shared/models/page/page-link';
import { MatDialog, MatPaginator, MatSort } from '@angular/material';
import { EntitiesDataSource } from '@shared/models/datasource/entity-datasource';
import { debounceTime, distinctUntilChanged, tap } from 'rxjs/operators';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { forkJoin, fromEvent, merge, Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { BaseData, HasId } from '@shared/models/base-data';
import { EntityId } from '@shared/models/id/entity-id';
import { ActivatedRoute } from '@angular/router';
import {
  CellActionDescriptor,
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor,
  HeaderActionDescriptor
} from '@shared/components/entity/entities-table-config.models';
import { EntityTypeTranslation } from '@shared/models/entity-type.models';
import { DialogService } from '@core/services/dialog.service';
import { AddEntityDialogComponent } from '@shared/components/entity/add-entity-dialog.component';
import {
  AddEntityDialogData,
  EntityAction
} from '@shared/components/entity/entity-component.models';
import { Timewindow } from '@shared/models/time/time.models';
import { DomSanitizer } from '@angular/platform-browser';
import { TbAnchorComponent } from '@shared/components/tb-anchor.component';

@Component({
  selector: 'tb-entities-table',
  templateUrl: './entities-table.component.html',
  styleUrls: ['./entities-table.component.scss']
})
export class EntitiesTableComponent extends PageComponent implements AfterViewInit, OnInit {

  @Input()
  entitiesTableConfig: EntityTableConfig<BaseData<HasId>>;

  translations: EntityTypeTranslation;

  headerActionDescriptors: Array<HeaderActionDescriptor>;
  groupActionDescriptors: Array<GroupActionDescriptor<BaseData<HasId>>>;
  cellActionDescriptors: Array<CellActionDescriptor<BaseData<HasId>>>;

  columns: Array<EntityTableColumn<BaseData<HasId>>>;
  displayedColumns: string[] = [];

  selectionEnabled;

  pageLink: PageLink;
  textSearchMode = false;
  timewindow: Timewindow;
  dataSource: EntitiesDataSource<BaseData<HasId>>;

  isDetailsOpen = false;

  @ViewChild('entityTableHeader', {static: false}) entityTableHeaderAnchor: TbAnchorComponent;

  @ViewChild('searchInput', {static: false}) searchInputField: ElementRef;

  @ViewChild(MatPaginator, {static: false}) paginator: MatPaginator;
  @ViewChild(MatSort, {static: false}) sort: MatSort;

  constructor(protected store: Store<AppState>,
              private route: ActivatedRoute,
              public translate: TranslateService,
              public dialog: MatDialog,
              private dialogService: DialogService,
              private domSanitizer: DomSanitizer,
              private componentFactoryResolver: ComponentFactoryResolver) {
    super(store);
  }

  ngOnInit() {
    this.entitiesTableConfig = this.entitiesTableConfig || this.route.snapshot.data.entitiesTableConfig;
    if (this.entitiesTableConfig.headerComponent) {
      const componentFactory = this.componentFactoryResolver.resolveComponentFactory(this.entitiesTableConfig.headerComponent);
      const viewContainerRef = this.entityTableHeaderAnchor.viewContainerRef;
      viewContainerRef.clear();
      const componentRef = viewContainerRef.createComponent(componentFactory);
      const headerComponent = componentRef.instance;
      headerComponent.entitiesTableConfig = this.entitiesTableConfig;
    }

    this.entitiesTableConfig.table = this;
    this.translations = this.entitiesTableConfig.entityTranslations;

    this.headerActionDescriptors = [...this.entitiesTableConfig.headerActionDescriptors];
    this.groupActionDescriptors = [...this.entitiesTableConfig.groupActionDescriptors];
    this.cellActionDescriptors = [...this.entitiesTableConfig.cellActionDescriptors];

    if (this.entitiesTableConfig.entitiesDeleteEnabled) {
      this.cellActionDescriptors.push(
        {
          name: this.translate.instant('action.delete'),
          icon: 'delete',
          isEnabled: entity => this.entitiesTableConfig.deleteEnabled(entity),
          onAction: ($event, entity) => this.deleteEntity($event, entity)
        }
      );
    }

    this.groupActionDescriptors.push(
      {
        name: this.translate.instant('action.delete'),
        icon: 'delete',
        isEnabled: this.entitiesTableConfig.entitiesDeleteEnabled,
        onAction: ($event, entities) => this.deleteEntities($event, entities)
      }
    );

    this.columns = [...this.entitiesTableConfig.columns];

    this.selectionEnabled = this.entitiesTableConfig.selectionEnabled;

    if (this.selectionEnabled) {
      this.displayedColumns.push('select');
    }
    this.columns.forEach(
      (column) => {
        this.displayedColumns.push(column.key);
      }
    );
    this.displayedColumns.push('actions');

    const sortOrder: SortOrder = { property: this.entitiesTableConfig.defaultSortOrder.property,
                                   direction: this.entitiesTableConfig.defaultSortOrder.direction };

    if (this.entitiesTableConfig.useTimePageLink) {
      this.timewindow = Timewindow.historyInterval(24 * 60 * 60 * 1000);
      const currentTime = new Date().getTime();
      this.pageLink = new TimePageLink(10, 0, null, sortOrder,
        currentTime - this.timewindow.history.timewindowMs, currentTime);
    } else {
      this.pageLink = new PageLink(10, 0, null, sortOrder);
    }
    this.dataSource = new EntitiesDataSource<BaseData<HasId>>(
      this.entitiesTableConfig.entitiesFetchFunction
    );
    if (this.entitiesTableConfig.onLoadAction) {
      this.entitiesTableConfig.onLoadAction(this.route);
    }
    if (this.entitiesTableConfig.loadDataOnInit) {
      this.dataSource.loadEntities(this.pageLink);
    }
  }

  ngAfterViewInit() {

    fromEvent(this.searchInputField.nativeElement, 'keyup')
      .pipe(
        debounceTime(150),
        distinctUntilChanged(),
        tap(() => {
          this.paginator.pageIndex = 0;
          this.updateData();
        })
      )
      .subscribe();

    this.sort.sortChange.subscribe(() => this.paginator.pageIndex = 0);

    merge(this.sort.sortChange, this.paginator.page)
      .pipe(
        tap(() => this.updateData())
      )
      .subscribe();
  }

  addEnabled() {
    return this.entitiesTableConfig.addEnabled;
  }

  updateData(closeDetails: boolean = true) {
    if (closeDetails) {
      this.isDetailsOpen = false;
    }
    this.pageLink.page = this.paginator.pageIndex;
    this.pageLink.pageSize = this.paginator.pageSize;
    this.pageLink.sortOrder.property = this.sort.active;
    this.pageLink.sortOrder.direction = Direction[this.sort.direction.toUpperCase()];
    if (this.entitiesTableConfig.useTimePageLink) {
      const timePageLink = this.pageLink as TimePageLink;
      if (this.timewindow.history.timewindowMs) {
        const currentTime = new Date().getTime();
        timePageLink.startTime = currentTime - this.timewindow.history.timewindowMs;
        timePageLink.endTime = currentTime;
      } else {
        timePageLink.startTime = this.timewindow.history.fixedTimewindow.startTimeMs;
        timePageLink.endTime = this.timewindow.history.fixedTimewindow.endTimeMs;
      }
    }
    this.dataSource.loadEntities(this.pageLink);
  }

  onRowClick($event: Event, entity) {
    if ($event) {
      $event.stopPropagation();
    }
    if (this.dataSource.toggleCurrentEntity(entity)) {
      this.isDetailsOpen = true;
    } else {
      this.isDetailsOpen = !this.isDetailsOpen;
    }
  }

  addEntity($event: Event) {
    let entity$: Observable<BaseData<HasId>>;
    if (this.entitiesTableConfig.addEntity) {
      entity$ = this.entitiesTableConfig.addEntity();
    } else {
      entity$ = this.dialog.open<AddEntityDialogComponent, AddEntityDialogData<BaseData<HasId>>,
                                 BaseData<HasId>>(AddEntityDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data: {
          entitiesTableConfig: this.entitiesTableConfig
        }
      }).afterClosed();
    }
    entity$.subscribe(
      (entity) => {
        if (entity) {
          this.updateData();
        }
      }
    );
  }

  onEntityUpdated(entity: BaseData<HasId>) {
    this.updateData(false);
  }

  onEntityAction(action: EntityAction<BaseData<HasId>>) {
    if (action.action === 'delete') {
      this.deleteEntity(action.event, action.entity);
    }
  }

  deleteEntity($event: Event, entity: BaseData<HasId>) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.entitiesTableConfig.deleteEntityTitle(entity),
      this.entitiesTableConfig.deleteEntityContent(entity),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.entitiesTableConfig.deleteEntity(entity.id).subscribe(
          () => {
            this.updateData();
          }
        );
      }
    });
  }

  deleteEntities($event: Event, entities: BaseData<HasId>[]) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.entitiesTableConfig.deleteEntitiesTitle(entities.length),
      this.entitiesTableConfig.deleteEntitiesContent(entities.length),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        const tasks: Observable<any>[] = [];
        entities.forEach((entity) => {
          if (this.entitiesTableConfig.deleteEnabled(entity)) {
            tasks.push(this.entitiesTableConfig.deleteEntity(entity.id));
          }
        });
        forkJoin(tasks).subscribe(
          () => {
            this.updateData();
          }
        );
      }
    });
  }

  onTimewindowChange() {
    this.updateData();
  }

  enterFilterMode() {
    this.textSearchMode = true;
    this.pageLink.textSearch = '';
    setTimeout(() => {
      this.searchInputField.nativeElement.focus();
      this.searchInputField.nativeElement.setSelectionRange(0, 0);
    }, 10);
  }

  exitFilterMode() {
    this.textSearchMode = false;
    this.pageLink.textSearch = null;
    this.paginator.pageIndex = 0;
    this.updateData();
  }

  resetSortAndFilter(update: boolean = true) {
    this.pageLink.textSearch = null;
    if (this.entitiesTableConfig.useTimePageLink) {
      this.timewindow = Timewindow.historyInterval(24 * 60 * 60 * 1000);
    }
    this.paginator.pageIndex = 0;
    const sortable = this.sort.sortables.get(this.entitiesTableConfig.defaultSortOrder.property);
    this.sort.active = sortable.id;
    this.sort.direction = this.entitiesTableConfig.defaultSortOrder.direction === Direction.ASC ? 'asc' : 'desc';
    if (update) {
      this.updateData();
    }
  }

  cellContent(entity: BaseData<HasId>, column: EntityTableColumn<BaseData<HasId>>) {
    return this.domSanitizer.bypassSecurityTrustHtml(column.cellContentFunction(entity, column.key));
  }

  cellStyle(entity: BaseData<HasId>, column: EntityTableColumn<BaseData<HasId>>) {
    return {...column.cellStyleFunction(entity, column.key), ...{maxWidth: column.maxWidth}};
  }

}
