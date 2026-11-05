"""Meaningful duplicate review contracts; synthetic inputs, no business verification."""
import copy,math,unittest
from duplicate_identity_review import review,approximate_distance_m

def row(source='osm:node:1',lon=34.8,lat=32.0,**extra):
    return dict(source_id=source,phone='0501234567',extension='',name='Example shop',address='Example1City',longitude=lon,latitude=lat,**extra)
class DuplicateReviewContract(unittest.TestCase):
 def test_near_identical_points_are_flagged_but_never_merged(self):
    a=row();b=row('osm:node:2',34.800001);before=copy.deepcopy([a,b]);result=review([a,b]);self.assertEqual(before,[a,b]);self.assertEqual(0,result['rows_removed_or_merged']);self.assertEqual({'nearly_coincident_manual_review':1},result['group_location_classes'])
 def test_separated_same_name_phone_address_remain_distinct(self):
    result=review([row(),row('osm:node:2',34.82)]);self.assertEqual({'separated_manual_review':1},result['group_location_classes']);self.assertEqual(0,result['rows_removed_or_merged'])
 def test_missing_addresses_do_not_create_identity_groups(self):
    a=row();b=row('osm:node:2');a['address']=b['address']='';result=review([a,b]);self.assertEqual([],result['same_name_phone_extension_address_groups']);self.assertEqual(2,result['rows_without_address'])
 def test_extensions_are_distinct_identity_keys(self):
    a=row();b=row('osm:node:2');a['extension']='11';b['extension']='12';result=review([a,b]);self.assertEqual([],result['same_name_phone_extension_address_groups']);self.assertEqual(1,result['shared_phones_with_multiple_source_ids'])
 def test_shared_call_center_different_names_are_not_merged(self):
    a=row();b=row('osm:node:2');b['name']='Different branch';result=review([a,b]);self.assertEqual([],result['same_name_phone_extension_address_groups']);self.assertEqual(2,len(result['shared_phone_groups'][0]['names']));self.assertEqual(0,result['rows_removed_or_merged'])
 def test_invalid_coordinate_means_manual_review_not_zero_distance(self):
    a=row();b=row('osm:node:2');b['latitude']=math.nan;self.assertIsNone(approximate_distance_m(a,b));self.assertEqual({'coordinates_unavailable_manual_review':1},review([a,b])['group_location_classes'])
 def test_repeated_source_row_count_is_explicit_not_extra_physical_node(self):
    a=row();result=review([a,copy.deepcopy(a)]);self.assertEqual(1,result['repeated_source_phone_extension_rows']);self.assertEqual(1,result['distinct_source_ids']);self.assertEqual([],result['same_name_phone_extension_address_groups'])
 def test_whitespace_case_normalization_does_not_rewrite_sources(self):
    a=row();b=row('osm:node:2');b['name']=' EXAMPLE  SHOP ';before=b['name'];result=review([a,b]);self.assertEqual(1,len(result['same_name_phone_extension_address_groups']));self.assertEqual(before,b['name'])
if __name__=='__main__':unittest.main(verbosity=2)
