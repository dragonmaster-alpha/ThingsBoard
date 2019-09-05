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
  Component,
  Input,
  OnChanges,
  OnInit,
  QueryList,
  SimpleChanges,
  ViewChild,
  ViewChildren
} from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { AuthUser } from '@shared/models/user.model';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { Timewindow } from '@shared/models/time/time.models';
import { TimeService } from '@core/services/time.service';
import { GridsterComponent, GridsterConfig, GridsterItemComponent } from 'angular-gridster2';
import {
  DashboardCallbacks,
  DashboardWidget,
  IDashboardComponent,
  WidgetsData
} from '../../models/dashboard-component.models';
import { merge, Observable } from 'rxjs';
import { map, share, tap } from 'rxjs/operators';
import { WidgetLayout } from '@shared/models/dashboard.models';
import { DialogService } from '@core/services/dialog.service';
import { animatedScroll, isDefined } from '@app/core/utils';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MediaBreakpoints } from '@shared/models/constants';
import { IAliasController, IStateController } from '@app/core/api/widget-api.models';

@Component({
  selector: 'tb-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent extends PageComponent implements IDashboardComponent, OnInit, AfterViewInit, OnChanges {

  authUser: AuthUser;

  @Input()
  widgetsData: Observable<WidgetsData>;

  @Input()
  callbacks: DashboardCallbacks;

  @Input()
  aliasController: IAliasController;

  @Input()
  stateController: IStateController;

  @Input()
  columns: number;

  @Input()
  horizontalMargin: number;

  @Input()
  verticalMargin: number;

  @Input()
  isEdit: boolean;

  @Input()
  autofillHeight: boolean;

  @Input()
  mobileAutofillHeight: boolean;

  @Input()
  mobileRowHeight: number;

  @Input()
  isMobile: boolean;

  @Input()
  isMobileDisabled: boolean;

  @Input()
  isEditActionEnabled: boolean;

  @Input()
  isExportActionEnabled: boolean;

  @Input()
  isRemoveActionEnabled: boolean;

  @Input()
  dashboardStyle: {[klass: string]: any};

  @Input()
  dashboardClass: string;

  @Input()
  ignoreLoading: boolean;

  @Input()
  dashboardTimewindow: Timewindow;

  gridsterOpts: GridsterConfig;

  dashboardLoading = true;

  highlightedMode = false;
  highlightedWidget: DashboardWidget = null;
  selectedWidget: DashboardWidget = null;

  isWidgetExpanded = false;
  isMobileSize = false;

  @ViewChild('gridster', {static: true}) gridster: GridsterComponent;

  @ViewChildren(GridsterItemComponent) gridsterItems: QueryList<GridsterItemComponent>;

  widgets$: Observable<Array<DashboardWidget>>;

  widgets: Array<DashboardWidget>;

  constructor(protected store: Store<AppState>,
              private timeService: TimeService,
              private dialogService: DialogService,
              private breakpointObserver: BreakpointObserver) {
    super(store);
    this.authUser = getCurrentAuthUser(store);
  }

  ngOnInit(): void {
    if (!this.dashboardTimewindow) {
      this.dashboardTimewindow = this.timeService.defaultTimewindow();
    }
    this.gridsterOpts = {
      gridType: 'scrollVertical',
      keepFixedHeightInMobile: true,
      pushItems: false,
      swap: false,
      maxRows: 100,
      minCols: this.columns ? this.columns : 24,
      outerMargin: true,
      outerMarginLeft: this.horizontalMargin ? this.horizontalMargin : 10,
      outerMarginRight: this.horizontalMargin ? this.horizontalMargin : 10,
      outerMarginTop: this.verticalMargin ? this.verticalMargin : 10,
      outerMarginBottom: this.horizontalMargin ? this.horizontalMargin : 10,
      minItemCols: 1,
      minItemRows: 1,
      defaultItemCols: 8,
      defaultItemRows: 6,
      resizable: {enabled: this.isEdit},
      draggable: {enabled: this.isEdit},
      itemChangeCallback: item => this.sortWidgets(this.widgets)
    };

    this.updateMobileOpts();

    this.loadDashboard();

    this.breakpointObserver
      .observe(MediaBreakpoints['gt-sm']).subscribe(
      () => {
        this.updateMobileOpts();
      }
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    let updateMobileOpts = false;
    let updateLayoutOpts = false;
    let updateEditingOpts = false;
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (['isMobile', 'isMobileDisabled', 'autofillHeight', 'mobileAutofillHeight', 'mobileRowHeight'].includes(propName)) {
          updateMobileOpts = true;
        } else if (['horizontalMargin', 'verticalMargin'].includes(propName)) {
          updateLayoutOpts = true;
        } else if (propName === 'isEdit') {
          updateEditingOpts = true;
        }
      }
    }
    if (updateMobileOpts) {
      this.updateMobileOpts();
    }
    if (updateLayoutOpts) {
      this.updateLayoutOpts();
    }
    if (updateEditingOpts) {
      this.updateEditingOpts();
    }
    if (updateMobileOpts || updateLayoutOpts || updateEditingOpts) {
      this.notifyGridsterOptionsChanged();
    }
  }

  loadDashboard() {
    this.widgets$ = this.widgetsData.pipe(
      map(widgetsData => {
        const dashboardWidgets = new Array<DashboardWidget>();
        let maxRows = this.gridsterOpts.maxRows;
        widgetsData.widgets.forEach(
          (widget) => {
            let widgetLayout: WidgetLayout;
            if (widgetsData.widgetLayouts && widget.id) {
              widgetLayout = widgetsData.widgetLayouts[widget.id];
            }
            const dashboardWidget = new DashboardWidget(this, widget, widgetLayout);
            const bottom = dashboardWidget.y + dashboardWidget.rows;
            maxRows = Math.max(maxRows, bottom);
            dashboardWidgets.push(dashboardWidget);
          }
        );
        this.sortWidgets(dashboardWidgets);
        this.gridsterOpts.maxRows = maxRows;
        return dashboardWidgets;
      }),
      tap((widgets) => {
        this.widgets = widgets;
        this.dashboardLoading = false;
      })
    );
  }

  reload() {
    this.loadDashboard();
  }

  sortWidgets(widgets?: Array<DashboardWidget>) {
    if (widgets) {
      widgets.sort((widget1, widget2) => {
        const row1 = widget1.widgetOrder;
        const row2 = widget2.widgetOrder;
        let res = row1 - row2;
        if (res === 0) {
          res = widget1.x - widget2.x;
        }
        return res;
      });
    }
  }

  ngAfterViewInit(): void {
  }

  isAutofillHeight(): boolean {
    if (this.isMobileSize) {
      return isDefined(this.mobileAutofillHeight) ? this.mobileAutofillHeight : false;
    } else {
      return isDefined(this.autofillHeight) ? this.autofillHeight : false;
    }
  }

  openDashboardContextMenu($event: Event) {
    // TODO:
    // this.dialogService.todo();
  }

  openWidgetContextMenu($event: Event, widget: DashboardWidget) {
    // TODO:
    // this.dialogService.todo();
  }

  onWidgetFullscreenChanged(expanded: boolean, widget: DashboardWidget) {
    this.isWidgetExpanded = expanded;
  }

  widgetMouseDown($event: Event, widget: DashboardWidget) {
    if (this.callbacks && this.callbacks.onWidgetMouseDown) {
      this.callbacks.onWidgetMouseDown($event, widget.widget);
    }
  }

  widgetClicked($event: Event, widget: DashboardWidget) {
    if (this.callbacks && this.callbacks.onWidgetClicked) {
      this.callbacks.onWidgetClicked($event, widget.widget);
    }
  }

  editWidget($event: Event, widget: DashboardWidget) {
    if ($event) {
      $event.stopPropagation();
    }
    if (this.isEditActionEnabled && this.callbacks && this.callbacks.onEditWidget) {
      this.callbacks.onEditWidget($event, widget.widget);
    }
  }

  exportWidget($event: Event, widget: DashboardWidget) {
    if ($event) {
      $event.stopPropagation();
    }
    if (this.isExportActionEnabled && this.callbacks && this.callbacks.onExportWidget) {
      this.callbacks.onExportWidget($event, widget.widget);
    }
  }

  removeWidget($event: Event, widget: DashboardWidget) {
    if ($event) {
      $event.stopPropagation();
    }
    if (this.isRemoveActionEnabled && this.callbacks && this.callbacks.onRemoveWidget) {
      this.callbacks.onRemoveWidget($event, widget.widget);
    }
  }

  highlightWidget(widget: DashboardWidget, delay?: number) {
    if (!this.highlightedMode || this.highlightedWidget !== widget) {
      this.highlightedMode = true;
      this.highlightedWidget = widget;
      this.scrollToWidget(widget, delay);
    }
  }

  selectWidget(widget: DashboardWidget, delay?: number) {
    if (this.selectedWidget !== widget) {
      this.selectedWidget = widget;
      this.scrollToWidget(widget, delay);
    }
  }

  resetHighlight() {
    this.highlightedMode = false;
    this.highlightedWidget = null;
    this.selectedWidget = null;
  }

  isHighlighted(widget: DashboardWidget) {
    return (this.highlightedMode && this.highlightedWidget === widget) || (this.selectedWidget === widget);
  }

  isNotHighlighted(widget: DashboardWidget) {
    return this.highlightedMode && this.highlightedWidget !== widget;
  }

  scrollToWidget(widget: DashboardWidget, delay?: number) {
    if (this.gridsterItems) {
      const gridsterItem = this.gridsterItems.find((item => item.item === widget));
      const offset = (this.gridster.curHeight - gridsterItem.height) / 2;
      let scrollTop = gridsterItem.top;
      if (offset > 0) {
        scrollTop -= offset;
      }
      const parentElement = this.gridster.el as HTMLElement;
      animatedScroll(parentElement, scrollTop, delay);
    }
  }

  private updateMobileOpts() {
    this.isMobileSize = this.checkIsMobileSize();
    const mobileBreakPoint = this.isMobileSize ? 20000 : 0;
    this.gridsterOpts.mobileBreakpoint = mobileBreakPoint;
    const rowSize = this.detectRowSize(this.isMobileSize);
    if (this.gridsterOpts.fixedRowHeight !== rowSize) {
      this.gridsterOpts.fixedRowHeight = rowSize;
    }
    if (this.isAutofillHeight()) {
      this.gridsterOpts.gridType = 'fit';
    } else {
      this.gridsterOpts.gridType = this.isMobileSize ? 'fixed' : 'scrollVertical';
    }
  }

  private updateLayoutOpts() {
    this.gridsterOpts.outerMarginLeft = this.horizontalMargin ? this.horizontalMargin : 10;
    this.gridsterOpts.outerMarginRight = this.horizontalMargin ? this.horizontalMargin : 10;
    this.gridsterOpts.outerMarginTop = this.verticalMargin ? this.verticalMargin : 10;
    this.gridsterOpts.outerMarginBottom = this.horizontalMargin ? this.horizontalMargin : 10;
  }

  private updateEditingOpts() {
    this.gridsterOpts.resizable.enabled = this.isEdit;
    this.gridsterOpts.draggable.enabled = this.isEdit;
  }

  private notifyGridsterOptionsChanged() {
    if (this.gridster && this.gridster.options) {
      this.gridster.optionsChanged();
    }
  }

  private detectRowSize(isMobile: boolean): number | null {
    let rowHeight = null;
    if (!this.isAutofillHeight()) {
      if (isMobile) {
        rowHeight = isDefined(this.mobileRowHeight) ? this.mobileRowHeight : 70;
      }
    }
    return rowHeight;
  }

  private checkIsMobileSize(): boolean {
    const isMobileDisabled = this.isMobileDisabled === true;
    let isMobileSize = this.isMobile === true && !isMobileDisabled;
    if (!isMobileSize && !isMobileDisabled) {
      isMobileSize = !this.breakpointObserver.isMatched(MediaBreakpoints['gt-sm']);
    }
    return isMobileSize;
  }

}
