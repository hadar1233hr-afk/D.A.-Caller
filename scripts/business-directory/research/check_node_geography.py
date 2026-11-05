"""Independent synthetic admission tests; no business number/currentness claims."""
import copy,importlib.util,math,pathlib,unittest
SCRIPT=pathlib.Path(__file__).with_name('research_node_geography.py')
spec=importlib.util.spec_from_file_location('node_geography_under_test',SCRIPT);geo=importlib.util.module_from_spec(spec);spec.loader.exec_module(geo)
def ring(x1,y1,x2,y2):return [{'coordinates':[[x1,y1],[x2,y1],[x2,y2],[x1,y2],[x1,y1]]}]
def shapes():return {'source_IL':ring(0,0,10,10),'Gaza_admin4':ring(7,1,8,2),'JudeaSamaria_admin4_outer_filled':ring(2,2,4,4),'WestBank_historic_outer_filled':ring(3,3,5,5)}
class GeographyContract(unittest.TestCase):
 def setUp(self):self.scope=geo.GeometryScope(shapes(),.001);self.addCleanup(self.scope.close)
 def test_clear_interior_is_research_only(self):self.assertEqual('research-qualified-source-interior',self.scope.classify(6,6));self.assertIn('research',geo.SCOPE)
 def test_outside_and_actual_boundary_rejected(self):
  for p in [(-1,5),(0,5),(10,10),(5,10)]:self.assertEqual('reject-outside-or-on-source-IL',self.scope.classify(*p))
 def test_inside_but_near_inclusion_edge_rejected(self):self.assertEqual('reject-near-source-IL-boundary',self.scope.classify(.0005,6))
 def test_union_of_nonidentical_full_exclusions(self):
  self.assertIn('JudeaSamaria',self.scope.classify(2.5,2.5));self.assertIn('WestBank_historic',self.scope.classify(4.5,4.5));self.assertIn('Gaza',self.scope.classify(7.5,1.5))
 def test_exclusion_boundaries_and_nearby_outside_rejected(self):
  for p in [(2,3),(1.9995,3),(8.0005,1.5)]:self.assertTrue(self.scope.classify(*p).startswith('reject-inside-or-near-'))
 def test_nonfinite_out_of_world_and_wrong_types(self):
  for p in [(math.nan,4),(3,math.inf),(181,0),(0,-91),('3',4),(True,4),(3,None)]:self.assertFalse(geo.valid_coordinates(*p));self.assertEqual('reject-invalid-coordinates',self.scope.classify(*p))
 def test_missing_geometry_never_accepted(self):
  r=shapes();del r['Gaza_admin4']
  with self.assertRaises(ValueError):geo.GeometryScope(r)
 def test_open_ring_never_accepted(self):
  r=shapes();r['source_IL'][0]['coordinates'].pop()
  with self.assertRaises(ValueError):geo.GeometryScope(r)
 def test_self_intersecting_ring_never_accepted(self):
  r=shapes();r['source_IL'][0]['coordinates']=[[0,0],[10,10],[0,10],[10,0],[0,0]]
  with self.assertRaises(ValueError):geo.GeometryScope(r)
 def test_margin_must_be_positive_bounded_and_not_nan(self):
  for m in [0,-1,.02,math.nan,math.inf]:
   with self.assertRaises(ValueError):geo.GeometryScope(shapes(),m)
class CountryContract(unittest.TestCase):
 def test_missing_country_is_not_invented(self):self.assertEqual(('missing-source-country-tag',None),geo.country_decision({}))
 def test_explicit_il_aliases_only(self):
  for tag in ['IL','ISR','ISRAEL','ישראל']:self.assertEqual('explicit-IL',geo.country_decision({'addr:country':tag})[0])
 def test_explicit_other_country_rejected(self):
  for tag in ['PS','LB','JO','DE','UNKNOWN']:self.assertEqual('reject-explicit-non-IL',geo.country_decision({'country':tag})[0])
 def test_secondary_geographic_fields_are_not_ignored(self):
  for field in ['is_in:country_code','is_in:country']:
   self.assertEqual('reject-explicit-non-IL',geo.country_decision({'addr:country':'IL',field:'PS'})[0])
   self.assertEqual('explicit-IL',geo.country_decision({field:'IL'})[0])
 def test_israeli_phone_and_city_do_not_invent_country(self):
  self.assertEqual(('missing-source-country-tag',None),geo.country_decision({'phone':'+972501234567','addr:city':'Tel Aviv'}))
 def test_conflicting_country_tags_rejected(self):
  for tags in [{'addr:country':'IL','country':'PS'},{'addr:country':'PS','country':'IL'},{'addr:country':'','country':'PS'}]:self.assertEqual('reject-explicit-non-IL',geo.country_decision(tags)[0])
if __name__=='__main__':unittest.main(verbosity=2)
