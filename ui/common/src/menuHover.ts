/* Based on: */
/*!
 * hoverIntent v1.10.0 // 2019.02.25 // jQuery v1.7.0+
 * http://briancherne.github.io/jquery-hoverIntent/
 *
 * You may use hoverIntent under the terms of the MIT license. Basically that
 * means you are free to use hoverIntent as long as this header is left intact.
 * Copyright 2007-2019 Brian Cherne
 */

type State = any;

export const menuHover = () => window.lidraughts.raf(function() {

  if (window.lidraughts.hasTouchEvents) return;

  let interval: number = 100;
  let sensitivity: number = 10;

  // current X and Y position of mouse, updated during mousemove tracking (shared across instances)
  let cX: number, cY: number;

  // saves the current pointer position coordinates based on the given mousemove event
  let track = function(ev: JQueryEventObject) {
    cX = ev.pageX;
    cY = ev.pageY;
  };

  // state properties:
  // timeoutId = timeout ID, reused for tracking mouse position and delaying "out" handler
  // isActive = plugin state, true after `over` is called just until `out` is called
  // pX, pY = previously-measured pointer coordinates, updated at each polling interval
  // event = string representing the namespaced event used for mouse tracking
  let state: State = {};

  $('#topnav.hover').each(function(this: HTMLElement) {

    const $el = $(this).removeClass('hover'),
      handler = () => $el.toggleClass('hover');


    // compares current and previous mouse positions
    const compare = function() {
      // compare mouse positions to see if pointer has slowed enough to trigger `over` function
      if ( Math.sqrt( (state.pX-cX)*(state.pX-cX) + (state.pY-cY)*(state.pY-cY) ) < sensitivity ) {
        $el.off(state.event, track);
        delete state.timeoutId;
        // set hoverIntent state as active for this element (permits `out` handler to trigger)
        state.isActive = true;
        handler();
      } else {
        // set previous coordinates for next comparison
        state.pX = cX; state.pY = cY;
        // use self-calling timeout, guarantees intervals are spaced out properly (avoids JavaScript timer bugs)
        state.timeoutId = setTimeout(compare, interval );
      }
    };

    // A private function for handling mouse 'hovering'
    var handleHover = function(ev: JQueryEventObject) {

      // clear any existing timeout
      if (state.timeoutId) { state.timeoutId = clearTimeout(state.timeoutId); }

      // namespaced event used to register and unregister mousemove tracking
      var mousemove = state.event = 'mousemove';

      // handle the event, based on its type
      if (ev.type == 'mouseenter') {
        // do nothing if already active or a button is pressed (dragging a piece)
        if (state.isActive || (ev.originalEvent as MouseEvent).buttons) return;
        // set "previous" X and Y position based on initial entry point
        state.pX = ev.pageX; state.pY = ev.pageY;
        // update "current" X and Y position based on mousemove
        $el.off(mousemove, track).on(mousemove, track);
        // start polling interval (self-calling timeout) to compare mouse coordinates over time
        state.timeoutId = setTimeout(compare, interval );
      } else { // "mouseleave"
        // do nothing if not already active
        if (!state.isActive) return;
        // unbind expensive mousemove event
        $el.off(mousemove,track);
        // if hoverIntent state is true, then call the mouseOut function after the specified delay
        state = {};
        handler();
      }
    };

    $el.on('mouseenter', handleHover).on('mouseleave', handleHover);
  });
});
