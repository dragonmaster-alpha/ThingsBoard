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
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  forwardRef,
  Input, OnDestroy,
  OnInit,
  ViewChild,
  ViewEncapsulation
} from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, Validator } from '@angular/forms';
import * as ace from 'ace-builds';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { ActionNotificationHide, ActionNotificationShow } from '@core/notification/notification.actions';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { UtilsService } from '@core/services/utils.service';
import { isUndefined } from '@app/core/utils';
import { TranslateService } from '@ngx-translate/core';
import { CancelAnimationFrame, RafService } from '@core/services/raf.service';

@Component({
  selector: 'tb-js-func',
  templateUrl: './js-func.component.html',
  styleUrls: ['./js-func.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => JsFuncComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => JsFuncComponent),
      multi: true,
    }
  ],
  encapsulation: ViewEncapsulation.None
})
export class JsFuncComponent implements OnInit, OnDestroy, ControlValueAccessor, Validator {

  @ViewChild('javascriptEditor', {static: true})
  javascriptEditorElmRef: ElementRef;

  private jsEditor: ace.Ace.Editor;
  private editorsResizeCaf: CancelAnimationFrame;
  private editorResizeListener: any;

  @Input() functionName: string;

  @Input() functionArgs: Array<string>;

  @Input() validationArgs: Array<any>;

  @Input() resultType: string;

  @Input() disabled: boolean;

  @Input() fillHeight: boolean;

  private noValidateValue: boolean;
  get noValidate(): boolean {
    return this.noValidateValue;
  }
  @Input()
  set noValidate(value: boolean) {
    this.noValidateValue = coerceBooleanProperty(value);
  }

  private requiredValue: boolean;
  get required(): boolean {
    return this.requiredValue;
  }
  @Input()
  set required(value: boolean) {
    this.requiredValue = coerceBooleanProperty(value);
  }

  functionArgsString = '';

  fullscreen = false;

  modelValue: string;

  functionValid = true;

  validationError: string;

  errorShowed = false;

  errorMarkers: number[] = [];
  errorAnnotationId = -1;

  private propagateChange = null;

  constructor(public elementRef: ElementRef,
              private utils: UtilsService,
              private translate: TranslateService,
              protected store: Store<AppState>,
              private raf: RafService) {
  }

  ngOnInit(): void {
    if (!this.resultType || this.resultType.length === 0) {
      this.resultType = 'nocheck';
    }
    if (this.functionArgs) {
      this.functionArgs.forEach((functionArg) => {
        if (this.functionArgsString.length > 0) {
          this.functionArgsString += ', ';
        }
        this.functionArgsString += functionArg;
      });
    }
    const editorElement = this.javascriptEditorElmRef.nativeElement;
    let editorOptions: Partial<ace.Ace.EditorOptions> = {
      mode: 'ace/mode/javascript',
      showGutter: true,
      showPrintMargin: true,
      readOnly: this.disabled
    };

    const advancedOptions = {
      enableSnippets: true,
      enableBasicAutocompletion: true,
      enableLiveAutocompletion: true
    };

    editorOptions = {...editorOptions, ...advancedOptions};
    this.jsEditor = ace.edit(editorElement, editorOptions);
    this.jsEditor.session.setUseWrapMode(true);
    this.jsEditor.setValue(this.modelValue ? this.modelValue : '', -1);
    this.jsEditor.on('change', () => {
      this.cleanupJsErrors();
      this.updateView();
    });
    this.editorResizeListener = this.onAceEditorResize.bind(this);
    // @ts-ignore
    addResizeListener(editorElement, this.editorResizeListener);
  }

  ngOnDestroy(): void {
    if (this.editorResizeListener) {
      const editorElement = this.javascriptEditorElmRef.nativeElement;
      // @ts-ignore
      removeResizeListener(editorElement, this.editorResizeListener);
    }
  }

  private onAceEditorResize() {
    if (this.editorsResizeCaf) {
      this.editorsResizeCaf();
      this.editorsResizeCaf = null;
    }
    this.editorsResizeCaf = this.raf.raf(() => {
      this.jsEditor.resize();
      this.jsEditor.renderer.updateFull();
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  public validate(c: FormControl) {
    return (this.functionValid) ? null : {
      jsFunc: {
        valid: false,
      },
    };
  }

  beautifyJs() {
    const res = js_beautify(this.modelValue, {indent_size: 4, wrap_line_length: 60});
    this.jsEditor.setValue(res ? res : '', -1);
    this.updateView();
  }

  validateOnSubmit(): void {
    if (!this.disabled) {
      this.cleanupJsErrors();
      this.functionValid = this.validateJsFunc();
      if (!this.functionValid) {
        this.propagateChange(this.modelValue);
        this.store.dispatch(new ActionNotificationShow(
          {
            message: this.validationError,
            type: 'error',
            target: 'jsFuncEditor',
            verticalPosition: 'bottom',
            horizontalPosition: 'left'
          }));
        this.errorShowed = true;
      }
    }
  }

  private validateJsFunc(): boolean {
    try {
      const toValidate = new Function(this.functionArgsString, this.modelValue);
      if (this.noValidate) {
        return true;
      }
      if (this.validationArgs) {
        let res: any;
        let validationError: any;
        for (const validationArg of this.validationArgs) {
          try {
            res = toValidate.apply(this, validationArg);
            validationError = null;
            break;
          } catch (e) {
            validationError = e;
          }
        }
        if (validationError) {
          throw validationError;
        }
        if (this.resultType !== 'nocheck') {
          if (this.resultType === 'any') {
            if (isUndefined(res)) {
              this.validationError = this.translate.instant('js-func.no-return-error');
              return false;
            }
          } else {
            const resType = typeof res;
            if (resType !== this.resultType) {
              this.validationError = this.translate.instant('js-func.return-type-mismatch', {type: this.resultType});
              return false;
            }
          }
        }
        return true;
      } else {
        return true;
      }
    } catch (e) {
      const details = this.utils.parseException(e);
      let errorInfo = 'Error:';
      if (details.name) {
        errorInfo += ' ' + details.name + ':';
      }
      if (details.message) {
        errorInfo += ' ' + details.message;
      }
      if (details.lineNumber) {
        errorInfo += '<br>Line ' + details.lineNumber;
        if (details.columnNumber) {
          errorInfo += ' column ' + details.columnNumber;
        }
        errorInfo += ' of script.';
      }
      this.validationError = errorInfo;
      if (details.lineNumber) {
        const line = details.lineNumber - 1;
        let column = 0;
        if (details.columnNumber) {
          column = details.columnNumber;
        }
        const errorMarkerId = this.jsEditor.session.addMarker(new ace.Range(line, 0, line, Infinity),
          'ace_active-line', 'screenLine');
        this.errorMarkers.push(errorMarkerId);
        const annotations = this.jsEditor.session.getAnnotations();
        const errorAnnotation: ace.Ace.Annotation = {
          row: line,
          column,
          text: details.message,
          type: 'error'
        };
        this.errorAnnotationId = annotations.push(errorAnnotation) - 1;
        this.jsEditor.session.setAnnotations(annotations);
      }
      return false;
    }
  }

  private cleanupJsErrors(): void {
    if (this.errorShowed) {
      this.store.dispatch(new ActionNotificationHide(
        {
          target: 'jsFuncEditor'
        }));
      this.errorShowed = false;
    }
    this.errorMarkers.forEach((errorMarker) => {
      this.jsEditor.session.removeMarker(errorMarker);
    });
    this.errorMarkers.length = 0;
    if (this.errorAnnotationId > -1) {
      const annotations = this.jsEditor.session.getAnnotations();
      annotations.splice(this.errorAnnotationId, 1);
      this.jsEditor.session.setAnnotations(annotations);
      this.errorAnnotationId = -1;
    }
  }

  writeValue(value: string): void {
    this.modelValue = value;
    if (this.jsEditor) {
      this.jsEditor.setValue(this.modelValue ? this.modelValue : '', -1);
    }
  }

  updateView() {
    const editorValue = this.jsEditor.getValue();
    if (this.modelValue !== editorValue) {
      this.modelValue = editorValue;
      this.functionValid = true;
      this.propagateChange(this.modelValue);
    }
  }

}
