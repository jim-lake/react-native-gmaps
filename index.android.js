'use strict';

const React = require('react-native');

const {
  requireNativeComponent,
  PropTypes,
  DeviceEventEmitter,
  NativeModules,
} = React;

const RNGMapsModule = NativeModules.RNGMapsModule;

/* RNGMAPS COMP */
const gmaps = {
  name: 'RNGMapsViewManager',
  propTypes: {
    center: PropTypes.object,
    zoomLevel: PropTypes.number,
    markers: PropTypes.array,
    zoomOnMarkers: PropTypes.bool,
    centerNextLocationFix: PropTypes.bool,

    /* Hackedy hack hack hack */
    scaleX: React.PropTypes.number,
    scaleY: React.PropTypes.number,
    translateX: React.PropTypes.number,
    translateY: React.PropTypes.number,
    rotation: React.PropTypes.number,
  },
};

let MapView = requireNativeComponent('RNGMapsViewManager', gmaps);

class RNGMaps extends React.Component {
  constructor(props) {
    super(props);
    this._event = null;
    this._error = null;
    this._markerClick = null;
    this._onMarkerClick = this._onMarkerClick.bind(this);
  }
  componentDidMount() {
    this._event = DeviceEventEmitter.addListener('mapChange', (e: Event) => {
      this.props.onMapChange&&this.props.onMapChange(e);
    });

    this._error = DeviceEventEmitter.addListener('mapError', (e: Event) => {
      console.log(`[GMAP_ERROR]: ${e.message}`);
      this.props.onMapError&&this.props.onMapError(e);
    });

    this._markerClick = DeviceEventEmitter.addListener('markerClick',this._onMarkerClick);
  }
  componentWillUnmount() {
    this._event && this._event.remove();
    this._error && this._error.remove();
    this._markerClick && this._markerClick.remove();
  }
  componentWillReceiveProps(nextProps) {
  }

  _onMarkerClick(e) {
    const marker = this.props.markers.find((m) => {
      return e.id == m.id;
    });
    if (marker && marker.onRightCalloutPress) {
      marker.onRightCalloutPress(e);
    }
  }

  render () {
    return (
      <MapView
        {...this.props}
      />
    );
  }
}

module.exports = RNGMaps;
